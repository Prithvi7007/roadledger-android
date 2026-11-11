package fun.cadacrew.roadledger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GarageDb extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "roadledger.db";
    private static final int VERSION = 3;

    public GarageDb(Context context) {
        this(context, DATABASE_NAME);
    }

    // Only instrumentation tests use an isolated database name.
    GarageDb(Context context, String databaseName) {
        super(context, databaseName, null, VERSION);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, make TEXT NOT NULL DEFAULT '', model TEXT NOT NULL DEFAULT '', unit_system INTEGER NOT NULL DEFAULT 0 CHECK(unit_system IN (0,1)))");
        db.execSQL("CREATE TABLE fuel (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE, date INTEGER NOT NULL, odometer REAL NOT NULL CHECK(odometer>=0), liters REAL NOT NULL CHECK(liters>0), cost REAL NOT NULL CHECK(cost>=0), full_tank INTEGER NOT NULL CHECK(full_tank IN (0,1)), missed_fillup INTEGER NOT NULL DEFAULT 0 CHECK(missed_fillup IN (0,1)))");
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE, date INTEGER NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL CHECK(amount>=0), odometer REAL NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE, date INTEGER NOT NULL, start_km REAL NOT NULL, end_km REAL NOT NULL, note TEXT NOT NULL DEFAULT '', CHECK(end_km>=start_km))");
        db.execSQL("CREATE INDEX idx_fuel_vehicle_odo ON fuel(vehicle_id,odometer)");
        db.execSQL("CREATE INDEX idx_expenses_vehicle_date ON expenses(vehicle_id,date)");
        db.execSQL("CREATE INDEX idx_trips_vehicle_date ON trips(vehicle_id,date)");
        createImportTables(db);
        createProductTables(db);
    }
    private static void createImportTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS import_vehicle_links (source_key TEXT PRIMARY KEY NOT NULL, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS imported_rows (row_key TEXT PRIMARY KEY NOT NULL, record_kind TEXT NOT NULL, vehicle_key TEXT NOT NULL, imported_at INTEGER NOT NULL)");
    }
    private static void createProductTables(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE fuel ADD COLUMN station_id INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE TABLE fuel_stations (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE, name TEXT NOT NULL, address TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX idx_stations_vehicle ON fuel_stations(vehicle_id)");
        db.execSQL("CREATE TABLE service_items (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE, title TEXT NOT NULL, due_date INTEGER NOT NULL DEFAULT 0, due_km REAL NOT NULL DEFAULT 0 CHECK(due_km>=0), note TEXT NOT NULL DEFAULT '', completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1)))");
        db.execSQL("CREATE INDEX idx_services_vehicle ON service_items(vehicle_id,completed,due_date)");
        db.execSQL("CREATE TABLE expense_categories (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE, name TEXT NOT NULL, UNIQUE(vehicle_id,name))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<1 || oldVersion>newVersion || newVersion!=VERSION)
            throw new IllegalStateException("Unsupported database migration " + oldVersion + " to " + newVersion);
        if(oldVersion==1) {
            db.execSQL("ALTER TABLE vehicles ADD COLUMN unit_system INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE fuel ADD COLUMN missed_fillup INTEGER NOT NULL DEFAULT 0");
            createImportTables(db);
        }
        if(oldVersion<3) createProductTables(db);
    }

    public static final class Vehicle {
        public final long id;
        public final String name,make,model;
        /** 0 = km / liters, 1 = miles / US gallons. Storage is always metric. */
        public final int unitSystem;
        Vehicle(long id,String name,String make,String model,int units) {
            this.id=id; this.name=name; this.make=make; this.model=model; this.unitSystem=units;
        }
    }
    public static final class Fuel {
        public final long id,vehicleId,date;
        public final double odometer,liters,cost;
        public final boolean full, missed;
        public final long stationId;
        Fuel(long id,long vehicleId,long date,double odo,double liters,double cost,boolean full,boolean missed,long stationId) {
            this.id=id; this.vehicleId=vehicleId; this.date=date; this.odometer=odo; this.liters=liters; this.cost=cost; this.full=full; this.missed=missed; this.stationId=stationId;
        }
    }
    public static final class Expense {
        public final long id,vehicleId,date;
        public final double amount,odometer;
        public final String category,note;
        Expense(long id,long vehicleId,long date,String category,double amount,double odometer,String note) {
            this.id=id;this.vehicleId=vehicleId;this.date=date;this.category=category;this.amount=amount;this.odometer=odometer;this.note=note;
        }
    }
    public static final class Trip {
        public final long id,vehicleId,date;
        public final double startKm,endKm;
        public final String note;
        Trip(long id,long vehicleId,long date,double start,double end,String note) {
            this.id=id;this.vehicleId=vehicleId;this.date=date;this.startKm=start;this.endKm=end;this.note=note;
        }
    }
    public static final class Economy {
        public final double litersPer100Km,totalDistanceKm,totalLiters;
        public final List<Double> intervalValues;
        Economy(double km,double liters,List<Double> values) {
            totalDistanceKm=km;
            totalLiters=liters;
            litersPer100Km=km>0 ? 100d*liters/km : Double.NaN;
            intervalValues=Collections.unmodifiableList(values);
        }
    }

    public List<Vehicle> vehicles() {
        ArrayList<Vehicle> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,make,model,unit_system FROM vehicles ORDER BY id DESC",null)) {
            while(c.moveToNext()) result.add(new Vehicle(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)));
        }
        return result;
    }
    public long addVehicle(String name,String make,String model) {
        ContentValues v=new ContentValues();
        v.put("name",name.trim());v.put("make",make.trim());v.put("model",model.trim());
        return getWritableDatabase().insertOrThrow("vehicles",null,v);
    }
    /** Record updates preserve original IDs and import provenance. */
    public void updateVehicle(long id,String name,String make,String model) {
        if(name==null||name.trim().isEmpty())throw new IllegalArgumentException("Vehicle name is required.");
        ContentValues x=new ContentValues();
        x.put("name",name.trim());x.put("make",make==null?"":make.trim());x.put("model",model==null?"":model.trim());
        if(getWritableDatabase().update("vehicles",x,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalArgumentException("Vehicle no longer exists.");
    }
    public void updateFuel(long vehicleId,long id,long date,double odo,double liters,double cost,boolean full,boolean missed) {
        if(date<=0||odo<0||liters<=0||cost<0||!Double.isFinite(odo)||!Double.isFinite(liters)||!Double.isFinite(cost))
            throw new IllegalArgumentException("Invalid fuel values.");
        ContentValues x=new ContentValues();
        x.put("date",date);x.put("odometer",odo);x.put("liters",liters);x.put("cost",cost);
        x.put("full_tank",full?1:0);x.put("missed_fillup",missed?1:0);
        if(getWritableDatabase().update("fuel",x,"id=? AND vehicle_id=?",new String[]{Long.toString(id),Long.toString(vehicleId)})!=1)
            throw new IllegalArgumentException("Fill-up no longer exists in this vehicle.");
    }
    public void updateExpense(long vehicleId,long id,long date,String category,double amount,double odo,String note) {
        if(date<=0||amount<0||odo<0||!Double.isFinite(amount)||!Double.isFinite(odo))
            throw new IllegalArgumentException("Invalid expense values.");
        if(category==null||category.trim().isEmpty())throw new IllegalArgumentException("Category is required.");
        ContentValues x=new ContentValues();
        x.put("date",date);x.put("category",category.trim());x.put("amount",amount);
        x.put("odometer",odo);x.put("note",note==null?"":note.trim());
        if(getWritableDatabase().update("expenses",x,"id=? AND vehicle_id=?",new String[]{Long.toString(id),Long.toString(vehicleId)})!=1)
            throw new IllegalArgumentException("Expense no longer exists in this vehicle.");
    }
    public void updateTrip(long vehicleId,long id,long date,double start,double end,String note) {
        if(date<=0||start<0||end<=start||!Double.isFinite(start)||!Double.isFinite(end))
            throw new IllegalArgumentException("Trip end must exceed the start odometer.");
        ContentValues x=new ContentValues();
        x.put("date",date);x.put("start_km",start);x.put("end_km",end);
        x.put("note",note==null?"":note.trim());
        if(getWritableDatabase().update("trips",x,"id=? AND vehicle_id=?",new String[]{Long.toString(id),Long.toString(vehicleId)})!=1)
            throw new IllegalArgumentException("Trip no longer exists in this vehicle.");
    }

    public void deleteVehicle(long id) {
        SQLiteDatabase sql=getWritableDatabase();
        sql.beginTransaction();
        try {
            // Allow re-import of a vehicle that has been explicitly deleted.
            sql.execSQL("DELETE FROM imported_rows WHERE vehicle_key IN (SELECT source_key FROM import_vehicle_links WHERE vehicle_id=?)",new Object[]{id});
            sql.delete("vehicles","id=?",new String[]{Long.toString(id)});
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
    }
    public List<Fuel> fuel(long vehicleId) {
        ArrayList<Fuel> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("fuel",
            new String[]{"id","vehicle_id","date","odometer","liters","cost","full_tank","missed_fillup","station_id"},
            "vehicle_id=?",new String[]{Long.toString(vehicleId)},null,null,"date DESC,id DESC")) {
            while(c.moveToNext()) result.add(new Fuel(c.getLong(0),c.getLong(1),c.getLong(2),c.getDouble(3),c.getDouble(4),c.getDouble(5),c.getInt(6)==1,c.getInt(7)==1,c.getLong(8)));
        }
        return result;
    }
    public long addFuel(long vehicleId,long date,double odo,double liters,double cost,boolean full) {
        if(odo<0 || liters<=0 || cost<0 || !Double.isFinite(odo) || !Double.isFinite(liters) || !Double.isFinite(cost)) throw new IllegalArgumentException("Invalid fuel values");
        ContentValues v=new ContentValues();v.put("vehicle_id",vehicleId);v.put("date",date);v.put("odometer",odo);
        v.put("liters",liters);v.put("cost",cost);v.put("full_tank",full?1:0);
        return getWritableDatabase().insertOrThrow("fuel",null,v);
    }
    public void deleteFuel(long id) {
        getWritableDatabase().delete("fuel","id=?",new String[]{Long.toString(id)});
    }
    public List<Expense> expenses(long vehicleId) {
        ArrayList<Expense> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("expenses",
            new String[]{"id","vehicle_id","date","category","amount","odometer","note"},
            "vehicle_id=?",new String[]{Long.toString(vehicleId)},null,null,"date DESC,id DESC")) {
            while(c.moveToNext()) result.add(new Expense(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getDouble(4),c.getDouble(5),c.getString(6)));
        }
        return result;
    }
    public long addExpense(long vehicleId,long date,String category,double amount,double odo,String note) {
        if(amount<0||odo<0||!Double.isFinite(amount)||!Double.isFinite(odo)) throw new IllegalArgumentException("Invalid expense values");
        ContentValues v=new ContentValues();v.put("vehicle_id",vehicleId);v.put("date",date);
        v.put("category",category.trim());v.put("amount",amount);v.put("odometer",odo);v.put("note",note.trim());
        return getWritableDatabase().insertOrThrow("expenses",null,v);
    }
    public void deleteExpense(long id) {
        getWritableDatabase().delete("expenses","id=?",new String[]{Long.toString(id)});
    }
    public List<Trip> trips(long vehicleId) {
        ArrayList<Trip> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("trips",
            new String[]{"id","vehicle_id","date","start_km","end_km","note"},
            "vehicle_id=?",new String[]{Long.toString(vehicleId)},null,null,"date DESC,id DESC")) {
            while(c.moveToNext()) result.add(new Trip(c.getLong(0),c.getLong(1),c.getLong(2),c.getDouble(3),c.getDouble(4),c.getString(5)));
        }
        return result;
    }
    public long addTrip(long vehicleId,long date,double start,double end,String note) {
        if(start<0||end<=start||!Double.isFinite(start)||!Double.isFinite(end)) throw new IllegalArgumentException("Trip ending odometer must exceed start");
        ContentValues v=new ContentValues();v.put("vehicle_id",vehicleId);v.put("date",date);
        v.put("start_km",start);v.put("end_km",end);v.put("note",note.trim());
        return getWritableDatabase().insertOrThrow("trips",null,v);
    }
    public void deleteTrip(long id) {
        getWritableDatabase().delete("trips","id=?",new String[]{Long.toString(id)});
    }

    public static final class Station {
        public final long id,vehicleId;
        public final String name,address;
        Station(long id,long vehicleId,String name,String address){this.id=id;this.vehicleId=vehicleId;this.name=name;this.address=address;}
    }
    public static final class Service {
        public final long id,vehicleId,dueDate;
        public final String title,note;
        public final double dueKm;
        public final boolean completed;
        Service(long id,long vehicleId,String title,long dueDate,double dueKm,String note,boolean completed){
            this.id=id;this.vehicleId=vehicleId;this.title=title;this.dueDate=dueDate;this.dueKm=dueKm;this.note=note;this.completed=completed;
        }
        public int priority(long now,double odometerKm) {
            if(completed)return 3;
            if((dueDate>0&&now>=dueDate)||(dueKm>0&&odometerKm>=dueKm))return 0;
            if((dueDate>0&&dueDate-now<=30L*86400000L)||(dueKm>0&&dueKm-odometerKm<=1000))return 1;
            return 2;
        }
    }
    public double currentOdometerKm(long vehicleId){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(km) FROM (SELECT odometer AS km FROM fuel WHERE vehicle_id=? UNION ALL SELECT end_km AS km FROM trips WHERE vehicle_id=?)",new String[]{Long.toString(vehicleId),Long.toString(vehicleId)})){
            return c.moveToFirst()&&!c.isNull(0)?c.getDouble(0):0d;
        }
    }
    public List<Station> stations(long vehicleId){
        ArrayList<Station> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("fuel_stations",new String[]{"id","vehicle_id","name","address"},"vehicle_id=?",new String[]{Long.toString(vehicleId)},null,null,"name COLLATE NOCASE")){
            while(c.moveToNext())out.add(new Station(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3)));
        }return out;
    }
    public long saveStation(long vehicleId,long id,String name,String address){
        if(name==null||name.trim().isEmpty())throw new IllegalArgumentException("Station name is required.");
        ContentValues v=new ContentValues();v.put("vehicle_id",vehicleId);v.put("name",name.trim());v.put("address",address==null?"":address.trim());
        if(id==0)return getWritableDatabase().insertOrThrow("fuel_stations",null,v);
        if(getWritableDatabase().update("fuel_stations",v,"id=? AND vehicle_id=?",new String[]{Long.toString(id),Long.toString(vehicleId)})!=1)
            throw new IllegalArgumentException("Station no longer exists.");
        return id;
    }
    public void deleteStation(long vehicleId,long stationId){
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try{
            ContentValues empty=new ContentValues();empty.put("station_id",0);
            sql.update("fuel",empty,"vehicle_id=? AND station_id=?",new String[]{Long.toString(vehicleId),Long.toString(stationId)});
            sql.delete("fuel_stations","id=? AND vehicle_id=?",new String[]{Long.toString(stationId),Long.toString(vehicleId)});
            sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }
    public String stationName(long stationId){
        if(stationId<=0)return "";
        try(Cursor c=getReadableDatabase().query("fuel_stations",new String[]{"name"},"id=?",new String[]{Long.toString(stationId)},null,null,null)){
            return c.moveToFirst()?c.getString(0):"Unknown station";
        }
    }
    public void setFuelStation(long vehicleId,long fuelId,long stationId){
        if(stationId>0){
            boolean exists=false;
            for(Station station:stations(vehicleId))if(station.id==stationId){exists=true;break;}
            if(!exists)throw new IllegalArgumentException("Station does not belong to this vehicle.");
        }
        ContentValues v=new ContentValues();v.put("station_id",stationId);
        if(getWritableDatabase().update("fuel",v,"id=? AND vehicle_id=?",new String[]{Long.toString(fuelId),Long.toString(vehicleId)})!=1)
            throw new IllegalArgumentException("Fill-up no longer exists.");
    }
    public List<Service> services(long vehicleId){
        ArrayList<Service> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("service_items",new String[]{"id","vehicle_id","title","due_date","due_km","note","completed"},"vehicle_id=?",new String[]{Long.toString(vehicleId)},null,null,"completed ASC,due_date ASC,id DESC")){
            while(c.moveToNext())out.add(new Service(c.getLong(0),c.getLong(1),c.getString(2),c.getLong(3),c.getDouble(4),c.getString(5),c.getInt(6)==1));
        }return out;
    }
    public long saveService(long vehicleId,long id,String title,long dueDate,double dueKm,String note,boolean completed){
        if(title==null||title.trim().isEmpty()||dueDate<0||dueKm<0||!Double.isFinite(dueKm)||(dueDate==0&&dueKm==0))
            throw new IllegalArgumentException("Enter a service title and a due date or mileage.");
        ContentValues v=new ContentValues();v.put("vehicle_id",vehicleId);v.put("title",title.trim());v.put("due_date",dueDate);v.put("due_km",dueKm);v.put("note",note==null?"":note.trim());v.put("completed",completed?1:0);
        if(id==0)return getWritableDatabase().insertOrThrow("service_items",null,v);
        if(getWritableDatabase().update("service_items",v,"id=? AND vehicle_id=?",new String[]{Long.toString(id),Long.toString(vehicleId)})!=1)
            throw new IllegalArgumentException("Service reminder no longer exists.");
        return id;
    }
    public void deleteService(long vehicleId,long id){getWritableDatabase().delete("service_items","id=? AND vehicle_id=?",new String[]{Long.toString(id),Long.toString(vehicleId)});}
    public int overdueServices(long vehicleId,long now){
        int count=0;double odo=currentOdometerKm(vehicleId);
        for(Service item:services(vehicleId))if(item.priority(now,odo)==0)count++;
        return count;
    }
    public void addCategory(long vehicleId,String name){
        if(name==null||name.trim().isEmpty())throw new IllegalArgumentException("Category name is required.");
        ContentValues v=new ContentValues();v.put("vehicle_id",vehicleId);v.put("name",name.trim());
        getWritableDatabase().insertWithOnConflict("expense_categories",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public List<String> customCategories(long vehicleId){
        ArrayList<String> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("expense_categories",new String[]{"name"},"vehicle_id=?",new String[]{Long.toString(vehicleId)},null,null,"name COLLATE NOCASE")){
            while(c.moveToNext())out.add(c.getString(0));
        }return out;
    }
    public String[] categoryOptions(long vehicleId){
        java.util.LinkedHashSet<String> all=new java.util.LinkedHashSet<>();
        java.util.Collections.addAll(all,"Maintenance","Repairs","Insurance","Parking","Tolls","Registration","Other");
        all.addAll(customCategories(vehicleId));
        for(Expense expense:expenses(vehicleId))all.add(expense.category);
        return all.toArray(new String[0]);
    }
    public void deleteCategory(long vehicleId,String name){
        // Existing expense records retain the category label to avoid data loss.
        getWritableDatabase().delete("expense_categories","vehicle_id=? AND name=?",new String[]{Long.toString(vehicleId),name});
    }
    /**
     * Independent, conservative full-to-full economy calculation.
     * The starting full fill-up is excluded from fuel consumed over the next
     * interval; partial purchases between consecutive full fill-ups are included.
     */
    public Economy economy(long vehicleId) {
        List<Fuel> ordered=new ArrayList<>(fuel(vehicleId));
        ordered.sort(Comparator.comparingDouble((Fuel f)->f.odometer).thenComparingLong(f->f.date).thenComparingLong(f->f.id));
        Fuel anchor=null;
        double sinceAnchor=0,kmTotal=0,litersTotal=0;
        ArrayList<Double> values=new ArrayList<>();
        for(Fuel f:ordered) {
            // Gaps flagged by the source make this interval's economy unreliable.
            if(f.missed) { anchor=null; sinceAnchor=0; continue; }
            if(anchor==null) {
                if(f.full) {anchor=f;sinceAnchor=0;}
                continue;
            }
            if(f.odometer<=anchor.odometer) continue;
            sinceAnchor+=f.liters;
            if(f.full) {
                double km=f.odometer-anchor.odometer;
                if(km>0.1 && sinceAnchor>0) {
                    kmTotal+=km;
                    litersTotal+=sinceAnchor;
                    values.add(100d*sinceAnchor/km);
                }
                anchor=f;
                sinceAnchor=0;
            }
        }
        return new Economy(kmTotal,litersTotal,values);
    }
}
