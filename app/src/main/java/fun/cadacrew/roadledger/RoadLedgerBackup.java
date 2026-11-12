package fun.cadacrew.roadledger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Own-data backup for RoadLedger. It preserves integer primary keys, original timestamps,
 * US/metric preferences, imported provenance, and all normalized v2 database fields.
 *
 * No network access; the user chooses the destination via Android's document picker.
 * The archive is not encrypted: advise users to select a private destination.
 */
public final class RoadLedgerBackup {
    private RoadLedgerBackup() {}

    static final int FORMAT_VERSION=1;
    static final int SCHEMA_VERSION=6;
    static final int MAX_ARCHIVE_BYTES=128*1024*1024;
    static final int MAX_TABLE_ROWS=200000;
    private static final String FORMAT="roadledger-native-backup";
    private static final String MANIFEST="manifest.json";
    private static final String RECORDS="records.json";

    // Parent table first for INSERT; child tables are removed in the opposite order.
    private static final LinkedHashMap<String,String[]> COLUMNS=new LinkedHashMap<>();
    static {
        COLUMNS.put("vehicles",new String[]{"id","name","make","model","unit_system","year","vin","registration","insurance","tank1_fuel","tank2_fuel","tank1_unit","tank2_unit","tank1_capacity","tank2_capacity","consumption_unit"});
        COLUMNS.put("fuel",new String[]{"id","vehicle_id","date","odometer","liters","cost","full_tank","missed_fillup","station_id","tank_number","source_unit","fuel_subtype","discount","weather"});
        COLUMNS.put("expenses",new String[]{"id","vehicle_id","date","category","amount","odometer","note"});
        COLUMNS.put("trips",new String[]{"id","vehicle_id","date","start_km","end_km","note"});
        COLUMNS.put("import_vehicle_links",new String[]{"source_key","vehicle_id"});
        COLUMNS.put("imported_rows",new String[]{"row_key","record_kind","vehicle_key","imported_at"});
        COLUMNS.put("fuel_stations",new String[]{"id","vehicle_id","name","address"});
        COLUMNS.put("service_items",new String[]{"id","vehicle_id","title","due_date","due_km","note","completed"});
        COLUMNS.put("expense_categories",new String[]{"id","vehicle_id","name"});
        COLUMNS.put("fuelio_archives",new String[]{"sha256","source_name","payload_base64","imported_at"});
        COLUMNS.put("import_diagnostics",new String[]{"id","archive_sha256","row_number","section","message"});
        COLUMNS.put("service_history",new String[]{"id","vehicle_id","title","date","odometer_km","cost","vendor","note","repeat_days","repeat_km"});
        COLUMNS.put("v7_vehicle_settings",new String[]{"vehicle_id","currency","warning_days","warning_km","reminders_muted","paired_bluetooth","trip_purpose"});
        COLUMNS.put("v7_routes",new String[]{"id","vehicle_id","trip_id","started","ended","distance_km","duration_seconds","purpose","note","start_lat","start_lon","end_lat","end_lon","completed"});
        COLUMNS.put("v7_route_points",new String[]{"id","route_id","time_ms","latitude","longitude","accuracy_m","altitude_m","speed_mps"});
        COLUMNS.put("v7_attachments",new String[]{"id","vehicle_id","record_kind","record_id","filename","mime_type","payload_base64","ocr_text","captured_at"});
        COLUMNS.put("v7_station_meta",new String[]{"station_id","favorite","rating","latitude","longitude","last_price","price_updated"});
        COLUMNS.put("v7_reminder_recurrence",new String[]{"service_id","every_days","every_km","last_notified"});
    }

    public static final class Snapshot {
        private final JSONObject data;
        private final JSONObject manifest;
        private Snapshot(JSONObject data,JSONObject manifest){this.data=data;this.manifest=manifest;}
        public String summary(){
            StringBuilder s=new StringBuilder();
            for(String table:COLUMNS.keySet()){
                s.append(table.replace('_',' ')).append(": ").append(data.optJSONArray(table).length()).append('\n');
            }
            return s.toString()+"\nThis replaces ALL current RoadLedger entries, including imported records. "
                +"This action cannot be undone unless you have a separate backup.";
        }
        public int count(String table){return data.optJSONArray(table).length();}
    }

    public static void write(GarageDb helper,OutputStream output) throws IOException {
        if(output==null)throw new IOException("Cannot write the selected backup document.");
        JSONObject content=new JSONObject();
        SQLiteDatabase sql=helper.getReadableDatabase();
        try{
            // A consistent read transaction while creating the snapshot. Do not mutate on backup.
            sql.beginTransactionNonExclusive();
            try{
                for(Map.Entry<String,String[]> table:COLUMNS.entrySet()){
                    JSONArray rows=new JSONArray();
                    try(Cursor c=sql.query(table.getKey(),table.getValue(),null,null,null,null,null)){
                        if(c.getCount()>MAX_TABLE_ROWS)throw new IOException("Too many records for "+table.getKey());
                        while(c.moveToNext()){
                            JSONObject row=new JSONObject();
                            for(int i=0;i<c.getColumnCount();i++){
                                String name=c.getColumnName(i);
                                int type=c.getType(i);
                                if(type==Cursor.FIELD_TYPE_NULL)row.put(name,JSONObject.NULL);
                                else if(type==Cursor.FIELD_TYPE_INTEGER)row.put(name,c.getLong(i));
                                else if(type==Cursor.FIELD_TYPE_FLOAT)row.put(name,c.getDouble(i));
                                else if(type==Cursor.FIELD_TYPE_STRING)row.put(name,c.getString(i));
                                else throw new IOException("Unsupported database field in "+table.getKey());
                            }
                            rows.put(row);
                        }
                    }
                    content.put(table.getKey(),rows);
                }
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
        }catch(JSONException e){throw new IOException("Backup serialization failed",e);}
        byte[] bytes=content.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_ARCHIVE_BYTES)throw new IOException("Backup too large for this version.");
        try{
            JSONObject manifest=new JSONObject();
            manifest.put("format",FORMAT);
            manifest.put("format_version",FORMAT_VERSION);
            manifest.put("schema_version",SCHEMA_VERSION);
            manifest.put("created_epoch_ms",System.currentTimeMillis());
            manifest.put("records_sha256",sha256(bytes));
            ZipOutputStream zip=new ZipOutputStream(output);
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(RECORDS));
            zip.write(bytes);
            zip.closeEntry();
            zip.finish();
            zip.flush();
        }catch(JSONException e){throw new IOException("Backup manifest failed",e);}
    }

    public static Snapshot read(InputStream input) throws IOException {
        if(input==null)throw new IOException("Backup document could not be opened.");
        byte[] manifest=null,records=null;
        int overall=0,entries=0;
        try(ZipInputStream zip=new ZipInputStream(input)){
            ZipEntry next;
            while((next=zip.getNextEntry())!=null){
                if(next.isDirectory())throw new IOException("Unexpected backup folder.");
                entries++;
                if(entries>2)throw new IOException("Backup contains unexpected files.");
                String name=next.getName();
                if(!MANIFEST.equals(name)&&!RECORDS.equals(name))
                    throw new IOException("Unknown backup entry: "+name);
                if((MANIFEST.equals(name)&&manifest!=null)||(RECORDS.equals(name)&&records!=null))
                    throw new IOException("Duplicate backup entry.");
                ByteArrayOutputStream buf=new ByteArrayOutputStream();
                byte[] chunk=new byte[8192];int read;
                while((read=zip.read(chunk))!=-1){
                    overall+=read;
                    if(overall>MAX_ARCHIVE_BYTES)throw new IOException("Backup exceeds 32 MB limit.");
                    buf.write(chunk,0,read);
                }
                if(MANIFEST.equals(name))manifest=buf.toByteArray();
                else records=buf.toByteArray();
                zip.closeEntry();
            }
        }
        if(manifest==null||records==null||entries!=2)
            throw new IOException("Not a complete RoadLedger backup.");
        try{
            JSONObject info=new JSONObject(new String(manifest,StandardCharsets.UTF_8));
            if(!FORMAT.equals(info.getString("format")) || info.getInt("format_version")!=FORMAT_VERSION)
                throw new IOException("Unsupported RoadLedger backup format.");
            int schema=info.getInt("schema_version");
            if(schema!=2 && schema!=3 && schema!=4 && schema!=5 && schema!=SCHEMA_VERSION)
                throw new IOException("Unsupported RoadLedger backup schema.");
            if(!sha256(records).equals(info.getString("records_sha256")))
                throw new IOException("Backup checksum mismatch; no data was changed.");
            JSONObject data=new JSONObject(new String(records,StandardCharsets.UTF_8));
            // v0.3 backups remain restorable after the v3 database migration.
            // Only missing optional v3 fields are filled with their schema defaults.
            if(schema==2) {
                for(String table:new String[]{"fuel_stations","service_items","expense_categories"})
                    data.put(table,new JSONArray());
                JSONArray oldFuel=data.getJSONArray("fuel");
                for(int i=0;i<oldFuel.length();i++) oldFuel.getJSONObject(i).put("station_id",0);
            }
            if(schema<=3){
                data.put("fuelio_archives",new JSONArray());
                data.put("import_diagnostics",new JSONArray());
                JSONArray olderFuel=data.getJSONArray("fuel");
                for(int j=0;j<olderFuel.length();j++) olderFuel.getJSONObject(j).put("tank_number",1);
            }
            if(schema<=4){
                JSONArray oldVehicles=data.getJSONArray("vehicles");
                for(int i=0;i<oldVehicles.length();i++){
                    JSONObject row=oldVehicles.getJSONObject(i);
                    row.put("year",0);row.put("vin","");row.put("registration","");row.put("insurance","");
                    row.put("tank1_fuel","Gasoline");row.put("tank2_fuel","Disabled");
                    row.put("tank1_unit",row.getInt("unit_system")==1?"US gal":"L");
                    row.put("tank2_unit",row.getInt("unit_system")==1?"US gal":"L");
                    row.put("tank1_capacity",0);row.put("tank2_capacity",0);row.put("consumption_unit","Auto");
                }
                JSONArray olderFuel=data.getJSONArray("fuel");
                java.util.HashSet<Long> secondTankVehicleIds=new java.util.HashSet<>();
                for(int i=0;i<olderFuel.length();i++){
                    JSONObject row=olderFuel.getJSONObject(i);
                    row.put("source_unit","L");row.put("fuel_subtype","");row.put("discount",0);row.put("weather","");
                    if(row.getInt("tank_number")==2)secondTankVehicleIds.add(row.getLong("vehicle_id"));
                }
                for(int i=0;i<oldVehicles.length();i++){
                    JSONObject row=oldVehicles.getJSONObject(i);
                    if(secondTankVehicleIds.contains(row.getLong("id")))row.put("tank2_fuel","Gasoline");
                }
            }
            if(schema<=5){
                for(String table:new String[]{"service_history","v7_vehicle_settings","v7_routes","v7_route_points","v7_attachments","v7_station_meta","v7_reminder_recurrence"})
                    data.put(table,new JSONArray());
            }
            for(Map.Entry<String,String[]> table:COLUMNS.entrySet()){
                JSONArray rows=data.getJSONArray(table.getKey());
                if(rows.length()>MAX_TABLE_ROWS)throw new IOException("Too many "+table.getKey()+" records.");
                for(int i=0;i<rows.length();i++){
                    JSONObject item=rows.getJSONObject(i);
                    for(String col:table.getValue()){
                        if(!item.has(col)||(item.isNull(col)&&!("v7_routes".equals(table.getKey())&&"trip_id".equals(col))))
                            throw new IOException("Missing "+table.getKey()+"."+col+" on row "+(i+1));
                    }
                }
            }
            return new Snapshot(data,info);
        }catch(JSONException e){throw new IOException("Invalid RoadLedger backup data",e);}
    }

    /** All-or-nothing replacement, with existing database unchanged on any exception. */
    public static void restore(GarageDb helper,Snapshot backup) throws IOException {
        if(backup==null)throw new IOException("No backup was selected.");
        SQLiteDatabase sql=helper.getWritableDatabase();
        sql.beginTransaction();
        try{
            String[] keys=COLUMNS.keySet().toArray(new String[0]);
            for(int i=keys.length-1;i>=0;i--)sql.delete(keys[i],null,null);
            for(Map.Entry<String,String[]> table:COLUMNS.entrySet()){
                JSONArray rows=backup.data.getJSONArray(table.getKey());
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.getJSONObject(i);
                    ContentValues values=new ContentValues();
                    for(String key:table.getValue()){
                        Object val=row.get(key);
                        if(val instanceof Integer)values.put(key,(Integer)val);
                        else if(val instanceof Long)values.put(key,(Long)val);
                        else if(val instanceof Double)values.put(key,(Double)val);
                        else if(val instanceof String)values.put(key,(String)val);
                        else if(val==JSONObject.NULL && "v7_routes".equals(table.getKey()) && "trip_id".equals(key))values.putNull(key);
                        else if(val instanceof Number)values.put(key,((Number)val).doubleValue());
                        else throw new IOException("Unsupported backup data in "+table.getKey());
                    }
                    sql.insertOrThrow(table.getKey(),null,values);
                }
                try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM "+table.getKey(),null)){
                    if(!c.moveToFirst()||c.getInt(0)!=rows.length())
                        throw new IOException("Backup restore count mismatch in "+table.getKey());
                }
            }
            try(Cursor c=sql.rawQuery("PRAGMA foreign_key_check",null)){
                if(c.moveToFirst())throw new IOException("Backup contains invalid linked records.");
            }
            sql.setTransactionSuccessful();
        }catch(JSONException|RuntimeException e){throw new IOException("Backup restore failed. Existing data was preserved.",e);}
        finally{sql.endTransaction();}
    }

    private static String sha256(byte[] bytes) throws IOException {
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] hash=md.digest(bytes);
            StringBuilder s=new StringBuilder();
            for(byte b:hash)s.append(String.format(java.util.Locale.US,"%02x",b&255));
            return s.toString();
        }catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 is unavailable",e);}
    }
}
