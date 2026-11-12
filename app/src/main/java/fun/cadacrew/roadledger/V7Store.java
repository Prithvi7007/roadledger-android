package fun.cadacrew.roadledger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import java.util.ArrayList;
import java.util.List;

/** RoadLedger v7 extension of the SAME transactional database as GarageDb and native backup. */
public final class V7Store {
    private final GarageDb helper;
    public V7Store(GarageDb helper){this.helper=helper;}

    /** Called only during GarageDb creation/migration; no separate unbacked-up data store. */
    public static void createSchema(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS service_history (id INTEGER PRIMARY KEY AUTOINCREMENT,vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,title TEXT NOT NULL,date INTEGER NOT NULL,odometer_km REAL NOT NULL DEFAULT 0,cost REAL NOT NULL DEFAULT 0,vendor TEXT NOT NULL DEFAULT '',note TEXT NOT NULL DEFAULT '',repeat_days INTEGER NOT NULL DEFAULT 0,repeat_km REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_service_history_vehicle ON service_history(vehicle_id,date)");
        db.execSQL("CREATE TABLE IF NOT EXISTS v7_vehicle_settings (vehicle_id INTEGER PRIMARY KEY REFERENCES vehicles(id) ON DELETE CASCADE,currency TEXT NOT NULL DEFAULT 'USD',warning_days INTEGER NOT NULL DEFAULT 14,warning_km REAL NOT NULL DEFAULT 500,reminders_muted INTEGER NOT NULL DEFAULT 0,paired_bluetooth TEXT NOT NULL DEFAULT '',trip_purpose TEXT NOT NULL DEFAULT 'Personal')");
        db.execSQL("CREATE TABLE IF NOT EXISTS v7_routes (id INTEGER PRIMARY KEY AUTOINCREMENT,vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,trip_id INTEGER REFERENCES trips(id) ON DELETE SET NULL,started INTEGER NOT NULL,ended INTEGER NOT NULL DEFAULT 0,distance_km REAL NOT NULL DEFAULT 0,duration_seconds REAL NOT NULL DEFAULT 0,purpose TEXT NOT NULL DEFAULT 'Personal',note TEXT NOT NULL DEFAULT '',start_lat REAL NOT NULL DEFAULT 0,start_lon REAL NOT NULL DEFAULT 0,end_lat REAL NOT NULL DEFAULT 0,end_lon REAL NOT NULL DEFAULT 0,completed INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_routes_vehicle ON v7_routes(vehicle_id,started)");
        db.execSQL("CREATE TABLE IF NOT EXISTS v7_route_points (id INTEGER PRIMARY KEY AUTOINCREMENT,route_id INTEGER NOT NULL REFERENCES v7_routes(id) ON DELETE CASCADE,time_ms INTEGER NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,accuracy_m REAL NOT NULL DEFAULT 0,altitude_m REAL NOT NULL DEFAULT 0,speed_mps REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_route_points_route ON v7_route_points(route_id,time_ms)");
        db.execSQL("CREATE TABLE IF NOT EXISTS v7_attachments (id INTEGER PRIMARY KEY AUTOINCREMENT,vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,record_kind TEXT NOT NULL,record_id INTEGER NOT NULL DEFAULT 0,filename TEXT NOT NULL DEFAULT '',mime_type TEXT NOT NULL,payload_base64 TEXT NOT NULL,ocr_text TEXT NOT NULL DEFAULT '',captured_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attachments_vehicle ON v7_attachments(vehicle_id,record_kind,record_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS v7_station_meta (station_id INTEGER PRIMARY KEY REFERENCES fuel_stations(id) ON DELETE CASCADE,favorite INTEGER NOT NULL DEFAULT 0,rating INTEGER NOT NULL DEFAULT 0,latitude REAL NOT NULL DEFAULT 0,longitude REAL NOT NULL DEFAULT 0,last_price REAL NOT NULL DEFAULT 0,price_updated INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS v7_reminder_recurrence (service_id INTEGER PRIMARY KEY REFERENCES service_items(id) ON DELETE CASCADE,every_days INTEGER NOT NULL DEFAULT 0,every_km REAL NOT NULL DEFAULT 0,last_notified INTEGER NOT NULL DEFAULT 0)");
    }

    public static final class ServiceLog {
        public final long id,date;
        public final String title,vendor,note;
        public final double odometerKm,cost;
        public final int repeatDays;
        public final double repeatKm;
        public ServiceLog(long i,long d,String t,String v,String n,double o,double c,int rd,double rk){id=i;date=d;title=t;vendor=v;note=n;odometerKm=o;cost=c;repeatDays=rd;repeatKm=rk;}
    }
    public static final class Route {
        public final long id,started,ended,tripId;
        public final double distanceKm;
        public final String purpose,note;
        public final boolean completed;
        Route(long id,long started,long ended,long tripId,double distanceKm,String purpose,String note,boolean completed){this.id=id;this.started=started;this.ended=ended;this.tripId=tripId;this.distanceKm=distanceKm;this.purpose=purpose;this.note=note;this.completed=completed;}
    }
    public static final class Point {
        public final long time;
        public final double lat,lon,alt,accuracy,speed;
        Point(long time,double lat,double lon,double alt,double accuracy,double speed){this.time=time;this.lat=lat;this.lon=lon;this.alt=alt;this.accuracy=accuracy;this.speed=speed;}
    }
    public static final class Attachment {
        public final long id,capturedAt,recordId;
        public final String kind,filename,mime,ocr;
        Attachment(long id,long time,long record,String kind,String file,String mime,String ocr){this.id=id;capturedAt=time;recordId=record;this.kind=kind;filename=file;this.mime=mime;this.ocr=ocr;}
    }
    public static final class Settings {
        public final String currency,bluetooth,purpose;
        public final int warningDays;
        public final double warningKm;
        public final boolean muted;
        Settings(String c,int d,double k,boolean m,String b,String p){currency=c;warningDays=d;warningKm=k;muted=m;bluetooth=b;purpose=p;}
    }
    private static String str(String x){return x==null?"":x.trim();}
    private static String[] ids(long... ids){String[] out=new String[ids.length];for(int i=0;i<ids.length;i++)out[i]=Long.toString(ids[i]);return out;}
    public List<ServiceLog> serviceHistory(long vehicle){
        ArrayList<ServiceLog> out=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().query("service_history",new String[]{"id","date","title","vendor","note","odometer_km","cost","repeat_days","repeat_km"},"vehicle_id=?",ids(vehicle),null,null,"date DESC,id DESC")){
            while(c.moveToNext())out.add(new ServiceLog(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getDouble(5),c.getDouble(6),c.getInt(7),c.getDouble(8)));
        }return out;
    }
    public long saveServiceHistory(long vehicle,long id,long date,String title,String vendor,double odo,double cost,String note,int days,double km){
        if(vehicle<=0||date<=0||str(title).isEmpty()||!Double.isFinite(odo)||odo<0||!Double.isFinite(cost)||cost<0||days<0||days>36500||!Double.isFinite(km)||km<0)throw new IllegalArgumentException("Invalid service log");
        ContentValues v=new ContentValues();v.put("vehicle_id",vehicle);v.put("date",date);v.put("title",str(title));v.put("vendor",str(vendor));v.put("odometer_km",odo);v.put("cost",cost);v.put("note",str(note));v.put("repeat_days",days);v.put("repeat_km",km);
        SQLiteDatabase db=helper.getWritableDatabase();if(id==0)return db.insertOrThrow("service_history",null,v);
        if(db.update("service_history",v,"id=? AND vehicle_id=?",ids(id,vehicle))!=1)throw new IllegalArgumentException("Service record missing");return id;
    }
    public void deleteServiceHistory(long vehicle,long id){helper.getWritableDatabase().delete("service_history","vehicle_id=? AND id=?",ids(vehicle,id));}

    /** Create a schedule from a service completion; operations share a transaction. */
    public long logAndSchedule(long vehicle,long date,String title,String vendor,double odo,double cost,String note,int repeatDays,double repeatKm){
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
            long id=saveServiceHistory(vehicle,0,date,title,vendor,odo,cost,note,repeatDays,repeatKm);
            if(repeatDays>0||repeatKm>0){
                long dueDate=V7Logic.nextDueDate(date,repeatDays);double dueKm=V7Logic.nextDueKm(odo,repeatKm);
                long serviceId=helper.saveService(vehicle,0,title,dueDate,dueKm,note,false);
                setRecurrence(serviceId,repeatDays,repeatKm);
            }
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    public void setRecurrence(long serviceId,int days,double km){
        if(days<0||days>36500||!Double.isFinite(km)||km<0)throw new IllegalArgumentException("Invalid recurrence");
        ContentValues cv=new ContentValues();cv.put("service_id",serviceId);cv.put("every_days",days);cv.put("every_km",km);
        helper.getWritableDatabase().insertWithOnConflict("v7_reminder_recurrence",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public long[] recurrence(long serviceId){
        try(Cursor c=helper.getReadableDatabase().query("v7_reminder_recurrence",new String[]{"every_days","every_km"},"service_id=?",ids(serviceId),null,null,null)){
            return c.moveToFirst()?new long[]{c.getLong(0),Math.round(c.getDouble(1))}:new long[]{0,0};
        }
    }
    /** Completing a recurring schedule creates the next occurrence and preserves the completed one. */
    public void completeReminder(long vehicle,GarageDb.Service item,long date,double odo){
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
            long[] repeat=recurrence(item.id);
            helper.saveService(vehicle,item.id,item.title,item.dueDate,item.dueKm,item.note,true);
            saveServiceHistory(vehicle,0,date,item.title,"",odo,0,item.note,(int)repeat[0],repeat[1]);
            if(repeat[0]>0||repeat[1]>0){
                long due=V7Logic.nextDueDate(date,(int)repeat[0]);double km=V7Logic.nextDueKm(odo,repeat[1]);
                long next=helper.saveService(vehicle,0,item.title,due,km,item.note,false);
                setRecurrence(next,(int)repeat[0],repeat[1]);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public Settings settings(long vehicle){
        try(Cursor c=helper.getReadableDatabase().query("v7_vehicle_settings",new String[]{"currency","warning_days","warning_km","reminders_muted","paired_bluetooth","trip_purpose"},"vehicle_id=?",ids(vehicle),null,null,null)){
            if(c.moveToFirst())return new Settings(c.getString(0),c.getInt(1),c.getDouble(2),c.getInt(3)!=0,c.getString(4),c.getString(5));
        }return new Settings("USD",14,500,false,"","Personal");
    }
    public void saveSettings(long vehicle,String currency,int days,double km,boolean muted,String bt,String purpose){
        if(!currency.matches("[A-Z]{3}")||days<0||days>3650||!Double.isFinite(km)||km<0)throw new IllegalArgumentException("Invalid preferences");
        ContentValues cv=new ContentValues();cv.put("vehicle_id",vehicle);cv.put("currency",currency);cv.put("warning_days",days);cv.put("warning_km",km);cv.put("reminders_muted",muted?1:0);cv.put("paired_bluetooth",str(bt));cv.put("trip_purpose",str(purpose));
        helper.getWritableDatabase().insertWithOnConflict("v7_vehicle_settings",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public long beginRoute(long vehicle,long start,String purpose){
        if(vehicle<=0||start<=0)throw new IllegalArgumentException("Invalid route");
        ContentValues v=new ContentValues();v.put("vehicle_id",vehicle);v.put("started",start);v.put("purpose",str(purpose));
        return helper.getWritableDatabase().insertOrThrow("v7_routes",null,v);
    }
    public long activeRoute(long vehicle){
        try(Cursor c=helper.getReadableDatabase().query("v7_routes",new String[]{"id"},"vehicle_id=? AND completed=0",ids(vehicle),null,null,"started DESC","1")){
            return c.moveToFirst()?c.getLong(0):0;
        }
    }
    public List<Route> routes(long vehicle){
        ArrayList<Route> out=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().query("v7_routes",new String[]{"id","started","ended","trip_id","distance_km","purpose","note","completed"},"vehicle_id=?",ids(vehicle),null,null,"started DESC")){
            while(c.moveToNext())out.add(new Route(c.getLong(0),c.getLong(1),c.getLong(2),c.isNull(3)?0:c.getLong(3),c.getDouble(4),c.getString(5),c.getString(6),c.getInt(7)==1));
        }return out;
    }
    public void point(long route,long time,double lat,double lon,double alt,double acc,double speed){
        if(route<=0||time<=0||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("Invalid GPS point");
        ContentValues v=new ContentValues();v.put("route_id",route);v.put("time_ms",time);v.put("latitude",lat);v.put("longitude",lon);v.put("altitude_m",Double.isFinite(alt)?alt:0);v.put("accuracy_m",Math.max(0,Double.isFinite(acc)?acc:0));v.put("speed_mps",Math.max(0,Double.isFinite(speed)?speed:0));
        helper.getWritableDatabase().insertOrThrow("v7_route_points",null,v);
    }
    public List<Point> points(long route){
        ArrayList<Point> out=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().query("v7_route_points",new String[]{"time_ms","latitude","longitude","altitude_m","accuracy_m","speed_mps"},"route_id=?",ids(route),null,null,"time_ms ASC")){
            while(c.moveToNext())out.add(new Point(c.getLong(0),c.getDouble(1),c.getDouble(2),c.getDouble(3),c.getDouble(4),c.getDouble(5)));
        }return out;
    }
    public void finishRoute(long route,long ended,double km,long tripId){
        if(ended<=0||km<0||!Double.isFinite(km))throw new IllegalArgumentException("Invalid end route");
        ContentValues cv=new ContentValues();cv.put("ended",ended);cv.put("distance_km",km);cv.put("completed",1);
        if(tripId>0)cv.put("trip_id",tripId);
        if(helper.getWritableDatabase().update("v7_routes",cv,"id=? AND completed=0",ids(route))!=1)throw new IllegalArgumentException("Route not active");
    }
    public long attach(long vehicle,String kind,long recordId,String filename,String mime,byte[] data,String ocr){
        if(vehicle<=0||data==null||data.length==0||data.length>2_000_000||!mime.startsWith("image/"))throw new IllegalArgumentException("Images must be under 2 MB");
        ContentValues cv=new ContentValues();cv.put("vehicle_id",vehicle);cv.put("record_kind",str(kind));cv.put("record_id",recordId);cv.put("filename",str(filename));cv.put("mime_type",mime);cv.put("payload_base64",Base64.encodeToString(data,Base64.NO_WRAP));cv.put("ocr_text",str(ocr));cv.put("captured_at",System.currentTimeMillis());
        return helper.getWritableDatabase().insertOrThrow("v7_attachments",null,cv);
    }
    public List<Attachment> attachments(long vehicle){
        ArrayList<Attachment> out=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().query("v7_attachments",new String[]{"id","captured_at","record_id","record_kind","filename","mime_type","ocr_text"},"vehicle_id=?",ids(vehicle),null,null,"captured_at DESC")){
            while(c.moveToNext())out.add(new Attachment(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6)));
        }return out;
    }
    public byte[] attachmentBytes(long vehicle,long id){
        try(Cursor c=helper.getReadableDatabase().query("v7_attachments",new String[]{"payload_base64"},"vehicle_id=? AND id=?",ids(vehicle,id),null,null,null)){
            if(!c.moveToFirst())throw new IllegalArgumentException("Attachment missing");
            return Base64.decode(c.getString(0),Base64.DEFAULT);
        }
    }
    public void setOcr(long vehicle,long id,String text){ContentValues cv=new ContentValues();cv.put("ocr_text",str(text));helper.getWritableDatabase().update("v7_attachments",cv,"vehicle_id=? AND id=?",ids(vehicle,id));}
    public void deleteAttachment(long vehicle,long id){helper.getWritableDatabase().delete("v7_attachments","vehicle_id=? AND id=?",ids(vehicle,id));}
    public void stationMeta(long station,boolean favorite,int rating,double lat,double lon,double price,long priceTime){
        if(station<=0||rating<0||rating>5||!Double.isFinite(price)||price<0||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("Invalid station details");
        ContentValues v=new ContentValues();v.put("station_id",station);v.put("favorite",favorite?1:0);v.put("rating",rating);v.put("latitude",lat);v.put("longitude",lon);v.put("last_price",price);v.put("price_updated",priceTime);
        helper.getWritableDatabase().insertWithOnConflict("v7_station_meta",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public static final class StationMeta {public final boolean favorite;public final int rating;public final double lat,lon,price;public final long priceTime;
        StationMeta(boolean favorite,int rating,double lat,double lon,double price,long time){this.favorite=favorite;this.rating=rating;this.lat=lat;this.lon=lon;this.price=price;this.priceTime=time;}}
    public StationMeta stationMeta(long station){
        try(Cursor c=helper.getReadableDatabase().query("v7_station_meta",new String[]{"favorite","rating","latitude","longitude","last_price","price_updated"},"station_id=?",ids(station),null,null,null)){
            if(c.moveToFirst())return new StationMeta(c.getInt(0)!=0,c.getInt(1),c.getDouble(2),c.getDouble(3),c.getDouble(4),c.getLong(5));
        }return new StationMeta(false,0,0,0,0,0);
    }
}
