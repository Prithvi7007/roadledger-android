package fun.cadacrew.roadledger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Original offline importer for Fuelio's sectioned CSV export.
 * No Fuelio application code, online authentication, or network access.
 * Parses a document chosen in Android's system document picker.
 * Records are added transactionally; stable source IDs prevent duplicate imports.
 */
public final class FuelioImport {
    private static final int MAX_BYTES=24*1024*1024;
    private static final int MAX_ROWS=80000;
    private FuelioImport(){}

    static final class Fuel {
        long date; double odometerKm,liters,cost;
        boolean full,missed; String key;
    }
    static final class Expense {
        long date; double odometerKm,amount;
        String title,category,note,key;
    }
    static final class Trip {
        long date; double startKm,endKm;
        String note,key;
    }
    public static final class Vehicle {
        public String name,make,model,sourceKey;
        int distUnit,fuelUnit;
        final Map<String,String> categories=new HashMap<>();
        final List<Fuel> fuels=new ArrayList<>();
        final List<Expense> expenses=new ArrayList<>();
        final List<Trip> trips=new ArrayList<>();
    }
    public static final class Preview {
        public final List<Vehicle> vehicles=new ArrayList<>();
        public int skipped,missed,stations,pictures,otherSections;
        public int fuels(){int count=0;for(Vehicle v:vehicles)count+=v.fuels.size();return count;}
        public int expenses(){int count=0;for(Vehicle v:vehicles)count+=v.expenses.size();return count;}
        public int trips(){int count=0;for(Vehicle v:vehicles)count+=v.trips.size();return count;}
        public String describe(){
            return vehicles.size()+" vehicle(s)\n"
                +fuels()+" fill-ups ("+missed+" missed-entry flags)\n"
                +expenses()+" expenses\n"
                +trips()+" trips\n\n"
                +"For imported US vehicles, miles, US gallons, and MPG are displayed. "
                +"The database stores standardized kilometer/liter values.\n\n"
                +(stations>0?stations+" favorite stations are not imported yet.\n":"")
                +(pictures>0?pictures+" picture references are not imported yet.\n":"")
                +(skipped>0?skipped+" unsupported or invalid rows were skipped.\n":"")
                +"Existing RoadLedger data will not be replaced. "
                +"Prices are assumed to be USD; check your source currency.";
        }
    }
    public static final class Outcome {
        public int vehicles,fuels,expenses,trips,duplicates;
        public long lastVehicleId=-1;
        public String summary(){
            return "Imported "+vehicles+" vehicles, "+fuels+" fill-ups, "
              +expenses+" expenses, "+trips+" trips. "
              +duplicates+" already imported records skipped.";
        }
    }

    public static Preview inspect(InputStream stream,String fileName) throws IOException {
        if(stream==null)throw new IOException("Cannot open the selected document.");
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        byte[] chunk=new byte[8192];int read,total=0;
        while((read=stream.read(chunk))!=-1){
            total+=read;
            if(total>MAX_BYTES)throw new IOException("CSV is larger than 24 MB.");
            buffer.write(chunk,0,read);
        }
        byte[] data=buffer.toByteArray();
        if(data.length==0)throw new IOException("The selected file is empty.");
        if(data.length>3&&data[0]=='P'&&data[1]=='K')
            throw new IOException("Choose a Fuelio CSV export, not a ZIP archive.");
        List<List<String>> rows=csvRows(data);
        Preview preview=new Preview();
        Vehicle current=null;
        String section="";
        Map<String,Integer> header=null;
        boolean nextIsHeader=false;
        for(List<String> row:rows) {
            if(row.isEmpty())continue;
            String first=row.get(0).trim();
            if(first.length()>0&&first.charAt(0)==0xFEFF)first=first.substring(1);
            if(first.startsWith("##")){
                section=first.substring(2).trim().toLowerCase(Locale.ROOT);
                header=null;nextIsHeader=true;continue;
            }
            if(nextIsHeader){
                header=new LinkedHashMap<>();
                for(int i=0;i<row.size();i++)header.put(normalize(row.get(i)),i);
                nextIsHeader=false;continue;
            }
            if(header==null)continue;
            try{
                switch(section){
                    case "vehicle": {
                        Vehicle v=new Vehicle();
                        v.name=cell(row,header,"name");
                        if(v.name.isEmpty())v.name="Imported vehicle";
                        v.make=cell(row,header,"make");
                        v.model=cell(row,header,"model");
                        v.distUnit=integer(cell(row,header,"distunit"),0);
                        v.fuelUnit=integer(cell(row,header,"fuelunit"),0);
                        String guid=cell(row,header,"guid");
                        String id=guid.isEmpty()?v.name+"|"+v.make+"|"+v.model:guid;
                        v.sourceKey="fuelio:vehicle:"+hash(id);
                        preview.vehicles.add(v);current=v;
                        break;
                    }
                    case "log":
                        if(current==null){preview.skipped++;break;}
                        fuel(row,header,current,preview);break;
                    case "costcategories":
                        if(current!=null)current.categories.put(
                            cell(row,header,"costtypeid"),cell(row,header,"name"));
                        break;
                    case "costs":
                        if(current==null){preview.skipped++;break;}
                        expense(row,header,current,preview);break;
                    case "triplog":
                        if(current==null){preview.skipped++;break;}
                        trip(row,header,current,preview);break;
                    case "favstations":preview.stations++;break;
                    case "pictures":preview.pictures++;break;
                    default:preview.otherSections++;break;
                }
            }catch(IllegalArgumentException e){
                preview.skipped++;
            }
        }
        if(preview.vehicles.isEmpty())throw new IOException(
            "No Fuelio vehicle found. Select a per-vehicle CSV export.");
        return preview;
    }

    private static void fuel(List<String> row,Map<String,Integer> h,Vehicle v,Preview out){
        String odoKey=firstHeader(h,"odo");
        String fuelKey=firstHeader(h,"fuel","fueltype","fuelunit","fuelconsumption");
        if(odoKey.isEmpty()||fuelKey.isEmpty())throw new IllegalArgumentException("Missing quantity fields");
        Fuel f=new Fuel();
        f.date=parseDate(cell(row,h,"data"));
        f.odometerKm=nonnegative(cell(row,h,odoKey));
        if(odoKey.contains("mi")||(!odoKey.contains("km")&&v.distUnit==1))
            f.odometerKm*=1.609344;
        f.liters=positive(cell(row,h,fuelKey));
        if(fuelKey.contains("usgallon"))f.liters*=3.785411784;
        else if(fuelKey.contains("ukgallon"))f.liters*=4.54609;
        else if(fuelKey.contains("lit")){/* no conversion */}
        else if(v.fuelUnit==1)f.liters*=3.785411784;
        else if(v.fuelUnit==2)f.liters*=4.54609;
        else if(v.fuelUnit!=0)throw new IllegalArgumentException("Unsupported fuel");
        f.cost=optional(cell(row,h,"priceoptional"),0);
        String full=cell(row,h,"full").toLowerCase(Locale.ROOT);
        f.full="1".equals(full)||"2".equals(full)||"yes".equals(full)||"true".equals(full);
        String missed=cell(row,h,"missed").toLowerCase(Locale.ROOT);
        f.missed="1".equals(missed)||"yes".equals(missed)||"true".equals(missed);
        String fallback=f.date+"|"+f.odometerKm+"|"+f.liters+"|"+f.cost+"|"+f.full;
        f.key=rowKey("fuel",v.sourceKey,cell(row,h,"guid"),fallback);
        v.fuels.add(f);
        if(f.missed)out.missed++;
    }

    private static void expense(List<String> row,Map<String,Integer> h,Vehicle v,Preview out){
        String income=cell(row,h,"isincome");
        if("1".equals(income)||"true".equalsIgnoreCase(income)){out.skipped++;return;}
        Expense e=new Expense();
        e.date=parseDate(cell(row,h,"date"));
        e.odometerKm=optional(cell(row,h,"odo"),0)*(v.distUnit==1?1.609344:1);
        e.amount=optional(cell(row,h,"cost"),0);
        e.title=cell(row,h,"costtitle");
        e.note=cell(row,h,"notes");
        e.category=v.categories.get(cell(row,h,"costtypeid"));
        if(e.category==null||e.category.trim().isEmpty())
            e.category=e.title.isEmpty()?"Imported expense":e.title;
        e.key=rowKey("expense",v.sourceKey,cell(row,h,"guid"),
                e.date+"|"+e.odometerKm+"|"+e.amount+"|"+e.title+"|"+e.note);
        v.expenses.add(e);
    }

    private static void trip(List<String> row,Map<String,Integer> h,Vehicle v,Preview out){
        Trip t=new Trip();
        t.date=parseDate(cell(row,h,"startdate"));
        double scale=v.distUnit==1?1.609344:1;
        t.startKm=optional(cell(row,h,"startodo"),0)*scale;
        t.endKm=optional(cell(row,h,"endodo"),0)*scale;
        String title=cell(row,h,"title"),note=cell(row,h,"tripnote");
        t.note=title.isEmpty()?note:title+(note.isEmpty()?"":" — "+note);
        if(t.endKm<=t.startKm){
            // Fuelio's TripDist GPS field is meters, not US miles.
            double gpsKm=optional(cell(row,h,"tripdist"),0)/1000.0;
            if(gpsKm<=0){out.skipped++;return;}
            t.startKm=0;t.endKm=gpsKm;
            t.note=(t.note.isEmpty()?"":t.note+" · ")
                +"GPS distance (source odometer unavailable)";
        }
        t.key=rowKey("trip",v.sourceKey,cell(row,h,"guid"),
                t.date+"|"+t.startKm+"|"+t.endKm+"|"+t.note);
        v.trips.add(t);
    }

    private static List<List<String>> csvRows(byte[] data) throws IOException {
        List<List<String>> rows=new ArrayList<>();
        try(PushbackReader reader=new PushbackReader(
                new InputStreamReader(new ByteArrayInputStream(data),StandardCharsets.UTF_8),1)) {
            StringBuilder field=new StringBuilder();
            List<String> row=new ArrayList<>();
            boolean quoted=false;
            int code;
            while((code=reader.read())!=-1){
                char c=(char)code;
                if(quoted){
                    if(c=='"'){
                        int next=reader.read();
                        if(next=='"')field.append('"');
                        else{quoted=false;if(next!=-1)reader.unread(next);}
                    }else field.append(c);
                }else{
                    if(c=='"'&&field.length()==0)quoted=true;
                    else if(c==','){row.add(field.toString());field.setLength(0);}
                    else if(c=='\r'||c=='\n'){
                        if(c=='\r'){
                            int next=reader.read();
                            if(next!='\n'&&next!=-1)reader.unread(next);
                        }
                        row.add(field.toString());field.setLength(0);
                        if(!blank(row))rows.add(row);
                        row=new ArrayList<>();
                        if(rows.size()>MAX_ROWS)throw new IOException("Too many CSV records.");
                    }else field.append(c);
                }
                if(field.length()>1000000)throw new IOException("Oversized CSV field.");
            }
            if(quoted)throw new IOException("Invalid quoted CSV value.");
            if(field.length()>0||!row.isEmpty()){
                row.add(field.toString());
                if(!blank(row))rows.add(row);
            }
        }
        return rows;
    }
    private static boolean blank(List<String> row){
        for(String s:row)if(!s.trim().isEmpty())return false;
        return true;
    }
    private static String normalize(String s){
        return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
    }
    private static String cell(List<String> row,Map<String,Integer> h,String key){
        Integer i=h.get(normalize(key));
        return i==null||i<0||i>=row.size()?"":row.get(i).trim();
    }
    private static String firstHeader(Map<String,Integer> h,String prefix,String... excluded){
        for(String k:h.keySet()){
            if(!k.startsWith(prefix))continue;
            boolean skip=false;
            for(String e:excluded)if(k.startsWith(e))skip=true;
            if(!skip)return k;
        }
        return "";
    }
    private static int integer(String s,int fallback){
        try{return Integer.parseInt(s);}catch(Exception e){return fallback;}
    }
    private static double positive(String s){
        double d=Double.parseDouble(s);
        if(!Double.isFinite(d)||d<=0)throw new IllegalArgumentException("Invalid fuel quantity");
        return d;
    }
    private static double nonnegative(String s){
        double d=Double.parseDouble(s);
        if(!Double.isFinite(d)||d<0)throw new IllegalArgumentException("Invalid quantity");
        return d;
    }
    private static double optional(String s,double fallback){
        return s==null||s.isEmpty()?fallback:nonnegative(s);
    }
    private static long parseDate(String s){
        for(String format:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd"}){
            SimpleDateFormat formatter=new SimpleDateFormat(format,Locale.US);
            formatter.setLenient(false);
            ParsePosition pos=new ParsePosition(0);
            Date d=formatter.parse(s,pos);
            if(d!=null&&pos.getIndex()==s.length())return d.getTime();
        }
        throw new IllegalArgumentException("Invalid date");
    }
    private static String hash(String s){
        try{
            byte[] dig=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();
            for(byte b:dig)result.append(String.format(Locale.US,"%02x",b&255));
            return result.toString();
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static String rowKey(String kind,String vehicle,String guid,String fallback){
        return hash(kind+"|"+vehicle+"|"+(guid.isEmpty()?fallback:guid));
    }

    public static Outcome apply(GarageDb helper,Preview preview){
        SQLiteDatabase db=helper.getWritableDatabase();
        Outcome result=new Outcome();
        db.beginTransaction();
        try{
            for(Vehicle v:preview.vehicles){
                long id;
                try(Cursor cursor=db.rawQuery(
                        "SELECT vehicle_id FROM import_vehicle_links WHERE source_key=?",
                        new String[]{v.sourceKey})){
                    if(cursor.moveToFirst()){
                        id=cursor.getLong(0);
                    }else{
                        ContentValues vehicle=new ContentValues();
                        vehicle.put("name",v.name);vehicle.put("make",v.make);
                        vehicle.put("model",v.model);
                        vehicle.put("unit_system",v.distUnit==1&&v.fuelUnit==1?1:0);
                        id=db.insertOrThrow("vehicles",null,vehicle);
                        ContentValues link=new ContentValues();
                        link.put("source_key",v.sourceKey);link.put("vehicle_id",id);
                        db.insertOrThrow("import_vehicle_links",null,link);
                        result.vehicles++;
                    }
                }
                result.lastVehicleId=id;
                // Preserve imported custom expense category names in the new catalog.
                for(String category:v.categories.values()) {
                    if(category==null||category.trim().isEmpty()) continue;
                    ContentValues cv=new ContentValues();
                    cv.put("vehicle_id",id);cv.put("name",category.trim());
                    db.insertWithOnConflict("expense_categories",null,cv,SQLiteDatabase.CONFLICT_IGNORE);
                }
                for(Fuel f:v.fuels){
                    if(!reserve(db,f.key,"fuel",v.sourceKey)){result.duplicates++;continue;}
                    ContentValues x=new ContentValues();
                    x.put("vehicle_id",id);x.put("date",f.date);
                    x.put("odometer",f.odometerKm);x.put("liters",f.liters);
                    x.put("cost",f.cost);x.put("full_tank",f.full?1:0);
                    x.put("missed_fillup",f.missed?1:0);
                    db.insertOrThrow("fuel",null,x);
                    result.fuels++;
                }
                for(Expense e:v.expenses){
                    if(!reserve(db,e.key,"expense",v.sourceKey)){result.duplicates++;continue;}
                    ContentValues x=new ContentValues();
                    x.put("vehicle_id",id);x.put("date",e.date);
                    x.put("odometer",e.odometerKm);x.put("amount",e.amount);
                    x.put("category",e.category);x.put("note",e.note);
                    db.insertOrThrow("expenses",null,x);
                    result.expenses++;
                }
                for(Trip t:v.trips){
                    if(!reserve(db,t.key,"trip",v.sourceKey)){result.duplicates++;continue;}
                    ContentValues x=new ContentValues();
                    x.put("vehicle_id",id);x.put("date",t.date);
                    x.put("start_km",t.startKm);x.put("end_km",t.endKm);
                    x.put("note",t.note);
                    db.insertOrThrow("trips",null,x);
                    result.trips++;
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return result;
    }
    private static boolean reserve(SQLiteDatabase db,String key,String kind,String vehicle){
        ContentValues v=new ContentValues();
        v.put("row_key",key);v.put("record_kind",kind);
        v.put("vehicle_key",vehicle);v.put("imported_at",System.currentTimeMillis());
        return db.insertWithOnConflict("imported_rows",null,v,
                SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
}
