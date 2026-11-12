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
    private static final int VERSION = 6;

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
        createV05Tables(db);
        createV06Tables(db);
        V7Store.createSchema(db);
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
    private static void createV05Tables(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE fuel ADD COLUMN tank_number INTEGER NOT NULL DEFAULT 1 CHECK(tank_number IN (1,2))");
        db.execSQL("CREATE INDEX idx_fuel_vehicle_tank_odo ON fuel(vehicle_id,tank_number,odometer)");
        // A verbatim, offline archive of the user's *own exported CSV* prevents loss of
        // source fields RoadLedger cannot yet interpret. Only local database/backup.
        db.execSQL("CREATE TABLE fuelio_archives (sha256 TEXT PRIMARY KEY NOT NULL, source_name TEXT NOT NULL, payload_base64 TEXT NOT NULL, imported_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE import_diagnostics (id INTEGER PRIMARY KEY AUTOINCREMENT, archive_sha256 TEXT NOT NULL REFERENCES fuelio_archives(sha256) ON DELETE CASCADE, row_number INTEGER NOT NULL, section TEXT NOT NULL, message TEXT NOT NULL)");
    }
    private static void createV06Tables(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE vehicles ADD COLUMN year INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN vin TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN registration TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN insurance TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN tank1_fuel TEXT NOT NULL DEFAULT 'Gasoline'");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN tank2_fuel TEXT NOT NULL DEFAULT 'Disabled'");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN tank1_unit TEXT NOT NULL DEFAULT 'L'");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN tank2_unit TEXT NOT NULL DEFAULT 'L'");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN tank1_capacity REAL NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN tank2_capacity REAL NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE vehicles ADD COLUMN consumption_unit TEXT NOT NULL DEFAULT 'Auto'");
        db.execSQL("ALTER TABLE fuel ADD COLUMN source_unit TEXT NOT NULL DEFAULT 'L'");
        db.execSQL("ALTER TABLE fuel ADD COLUMN fuel_subtype TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE fuel ADD COLUMN discount REAL NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE fuel ADD COLUMN weather TEXT NOT NULL DEFAULT ''");
        // v0.5 stored everything in native litres; existing imperial vehicles used US gal at input.
        db.execSQL("UPDATE vehicles SET tank1_unit='US gal',tank2_unit='US gal' WHERE unit_system=1");
        db.execSQL("UPDATE vehicles SET tank2_fuel='Gasoline' WHERE id IN (SELECT vehicle_id FROM fuel WHERE tank_number=2)");
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
        if(oldVersion<4) createV05Tables(db);
        if(oldVersion<5) createV06Tables(db);
        if(oldVersion<6) V7Store.createSchema(db);
    }

    public static final class Vehicle {
        public final long id;
        public final String name,make,model;
        /** 0 = km / liters, 1 = miles / US gallons. Storage is always metric. */
        public final int unitSystem,year;
        public final String vin,registration,insurance,tank1Fuel,tank2Fuel,tank1Unit,tank2Unit,consumptionUnit;
        public final double tank1Capacity,tank2Capacity;
        Vehicle(long id,String name,String make,String model,int units,int year,String vin,String registration,
                String insurance,String fuel1,String fuel2,String unit1,String unit2,double capacity1,double capacity2,String economy) {
            this.id=id; this.name=name; this.make=make; this.model=model; this.unitSystem=units;
            this.year=year;this.vin=vin;this.registration=registration;this.insurance=insurance;
            tank1Fuel=fuel1;tank2Fuel=fuel2;tank1Unit=unit1;tank2Unit=unit2;
            tank1Capacity=capacity1;tank2Capacity=capacity2;consumptionUnit=economy;
        }
        public String tankFuel(int tank){return tank==2?tank2Fuel:tank1Fuel;}
        public String tankUnit(int tank){return tank==2?tank2Unit:tank1Unit;}
    }
    public static final class Fuel {
        public final long id,vehicleId,date;
        public final double odometer,liters,cost;
        public final boolean full, missed;
        public final long stationId;
        public final int tankNumber;
        public final String sourceUnit,fuelSubtype,weather;
        public final double discount;
        Fuel(long id,long vehicleId,long date,double odo,double liters,double cost,boolean full,boolean missed,long stationId) {
            this(id,vehicleId,date,odo,liters,cost,full,missed,stationId,1);
        }
        Fuel(long id,long vehicleId,long date,double odo,double liters,double cost,boolean full,boolean missed,long stationId,int tankNumber) {
            this(id,vehicleId,date,odo,liters,cost,full,missed,stationId,tankNumber,"L","",0,"");
        }
        Fuel(long id,long vehicleId,long date,double odo,double liters,double cost,boolean full,boolean missed,
                long stationId,int tankNumber,String sourceUnit,String subtype,double discount,String weather) {
            this.id=id; this.vehicleId=vehicleId; this.date=date; this.odometer=odo; this.liters=liters;
            this.cost=cost; this.full=full; this.missed=missed; this.stationId=stationId; this.tankNumber=tankNumber;
            this.sourceUnit=sourceUnit;this.fuelSubtype=subtype;this.discount=discount;this.weather=weather;
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
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,make,model,unit_system,year,vin,registration,insurance,tank1_fuel,tank2_fuel,tank1_unit,tank2_unit,tank1_capacity,tank2_capacity,consumption_unit FROM vehicles ORDER BY id DESC",null)) {
            while(c.moveToNext()) result.add(new Vehicle(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4),c.getInt(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),c.getString(10),c.getString(11),c.getString(12),c.getDouble(13),c.getDouble(14),c.getString(15)));
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
    /** Per-vehicle configuration cannot reinterpret historical energy quantities in another physical unit. */
    public void updateVehiclePreferences(long id,int year,String vin,String registration,String insurance,int distance,
            String fuel1,String unit1,double capacity1,String fuel2,String unit2,double capacity2,String economy) {
        if(distance!=0&&distance!=1)throw new IllegalArgumentException("Distance must be kilometres or miles.");
        if(year<0||!Double.isFinite(capacity1)||!Double.isFinite(capacity2)||capacity1<0||capacity2<0)
            throw new IllegalArgumentException("Year or tank capacity is invalid.");
        if("Disabled".equals(fuel1))throw new IllegalArgumentException("Primary tank must have a fuel type.");
        FuelUnits.checkFuelUnit(fuel1,unit1);FuelUnits.checkFuelUnit(fuel2,unit2);
        if(!FuelUnits.contains(FuelUnits.ECONOMY,economy))throw new IllegalArgumentException("Unknown economy display unit.");
        String[] configured={unit1,unit2};
        SQLiteDatabase sql=getWritableDatabase();
        for(int tank=1;tank<=2;tank++){
            try(Cursor c=sql.rawQuery("SELECT DISTINCT source_unit FROM fuel WHERE vehicle_id=? AND tank_number=?",
                    new String[]{Long.toString(id),Integer.toString(tank)})){
                while(c.moveToNext()){
                    if(tank==2&&"Disabled".equals(fuel2))
                        throw new IllegalArgumentException("Tank 2 has existing fill-ups; keep its fuel type enabled.");
                    if(!FuelUnits.baseUnit(c.getString(0)).equals(FuelUnits.baseUnit(configured[tank-1])))
                        throw new IllegalArgumentException("This tank has history in a different physical unit; keep its unit family.");
                }
            }
        }
        ContentValues x=new ContentValues();
        x.put("year",year);x.put("vin",vin==null?"":vin.trim());
        x.put("registration",registration==null?"":registration.trim());
        x.put("insurance",insurance==null?"":insurance.trim());x.put("unit_system",distance);
        x.put("tank1_fuel",fuel1);x.put("tank1_unit",unit1);x.put("tank1_capacity",capacity1*FuelUnits.factor(unit1));
        x.put("tank2_fuel",fuel2);x.put("tank2_unit",unit2);x.put("tank2_capacity",capacity2*FuelUnits.factor(unit2));
        x.put("consumption_unit",economy);
        if(sql.update("vehicles",x,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalArgumentException("Vehicle not found.");
    }
    /** Create/update a complete fill-up atomically; discount has already been subtracted from net cost. */
    public long saveFuelEntry(long vehicleId,long id,long date,double odo,double quantity,double netCost,
            boolean full,boolean missed,int tank,long stationId,String sourceUnit,String subtype,double discount,String weather) {
        if(date<=0||odo<0||quantity<=0||netCost<0||discount<0||!Double.isFinite(odo)||
                !Double.isFinite(quantity)||!Double.isFinite(netCost)||!Double.isFinite(discount))
            throw new IllegalArgumentException("Invalid fill-up amount, odometer or discount.");
        if(tank!=1&&tank!=2)throw new IllegalArgumentException("Tank must be 1 or 2.");
        Vehicle vehicle=null;
        for(Vehicle candidate:vehicles())if(candidate.id==vehicleId){vehicle=candidate;break;}
        if(vehicle==null||"Disabled".equals(vehicle.tankFuel(tank)))
            throw new IllegalArgumentException("This tank is not enabled for this vehicle.");
        FuelUnits.checkFuelUnit(vehicle.tankFuel(tank),sourceUnit);
        if(!FuelUnits.baseUnit(sourceUnit).equals(FuelUnits.baseUnit(vehicle.tankUnit(tank))))
            throw new IllegalArgumentException("Selected unit is incompatible with the tank.");
        if(stationId>0){
            boolean found=false;
            for(Station station:stations(vehicleId))if(station.id==stationId){found=true;break;}
            if(!found)throw new IllegalArgumentException("Station is not assigned to this vehicle.");
        }
        ContentValues values=new ContentValues();
        values.put("date",date);values.put("odometer",odo);values.put("liters",quantity);
        values.put("cost",netCost);values.put("full_tank",full?1:0);values.put("missed_fillup",missed?1:0);
        values.put("tank_number",tank);values.put("station_id",stationId);values.put("source_unit",sourceUnit);
        values.put("fuel_subtype",subtype==null?"":subtype.trim());values.put("discount",discount);
        values.put("weather",weather==null?"":weather.trim());
        SQLiteDatabase sql=getWritableDatabase();
        if(id==0){values.put("vehicle_id",vehicleId);return sql.insertOrThrow("fuel",null,values);}
        if(sql.update("fuel",values,"id=? AND vehicle_id=?",new String[]{Long.toString(id),Long.toString(vehicleId)})!=1)
            throw new IllegalArgumentException("Fill-up no longer exists for this vehicle.");
        return id;
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
            new String[]{"id","vehicle_id","date","odometer","liters","cost","full_tank","missed_fillup","station_id","tank_number","source_unit","fuel_subtype","discount","weather"},
            "vehicle_id=?",new String[]{Long.toString(vehicleId)},null,null,"date DESC,id DESC")) {
            while(c.moveToNext()) result.add(new Fuel(c.getLong(0),c.getLong(1),c.getLong(2),c.getDouble(3),c.getDouble(4),c.getDouble(5),c.getInt(6)==1,c.getInt(7)==1,c.getLong(8),c.getInt(9),c.getString(10),c.getString(11),c.getDouble(12),c.getString(13)));
        }
        return result;
    }
    public long addFuel(long vehicleId,long date,double odo,double liters,double cost,boolean full) {
        if(odo<0 || liters<=0 || cost<0 || !Double.isFinite(odo) || !Double.isFinite(liters) || !Double.isFinite(cost)) throw new IllegalArgumentException("Invalid fuel values");
        ContentValues v=new ContentValues();v.put("vehicle_id",vehicleId);v.put("date",date);v.put("odometer",odo);
        v.put("liters",liters);v.put("cost",cost);v.put("full_tank",full?1:0);
        return getWritableDatabase().insertOrThrow("fuel",null,v);
    }
    /** Changes tank association without changing date, amount, ID or import GUID. */
    public void setFuelTank(long vehicleId,long fuelId,int tank) {
        if(tank!=1&&tank!=2)throw new IllegalArgumentException("Tank must be 1 or 2.");
        ContentValues x=new ContentValues();x.put("tank_number",tank);
        if(getWritableDatabase().update("fuel",x,"id=? AND vehicle_id=?",
            new String[]{Long.toString(fuelId),Long.toString(vehicleId)})!=1)
            throw new IllegalArgumentException("Fill-up could not be found.");
    }
    public int archivedFuelioFiles(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM fuelio_archives",null)){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    public static final class ArchivedSource {
        public final String sha256,filename;
        ArchivedSource(String sha,String name){sha256=sha;filename=name;}
    }
    public List<ArchivedSource> archivedSources(){
        ArrayList<ArchivedSource> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT sha256,source_name FROM fuelio_archives ORDER BY imported_at DESC",null)){
            while(c.moveToNext())result.add(new ArchivedSource(c.getString(0),c.getString(1)));
        }
        return result;
    }
    public String archivePayloadBase64(String sha){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT payload_base64 FROM fuelio_archives WHERE sha256=?",new String[]{sha})){
            if(!c.moveToFirst())throw new IllegalArgumentException("Archived source not found");
            return c.getString(0);
        }
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
    public Economy economy(long vehicleId) {return economy(vehicleId,1);}
    public Economy economy(long vehicleId,int tank) {
        List<RoadLedgerFuelMath.Fill> records=new ArrayList<>();
        for(Fuel x:fuel(vehicleId)){
            records.add(new RoadLedgerFuelMath.Fill(x.id,x.date,x.tankNumber,x.odometer,x.liters,x.cost,x.full,x.missed));
        }
        RoadLedgerFuelMath.Result result=RoadLedgerFuelMath.calculate(records,tank);
        List<Double> values=new ArrayList<>();
        for(RoadLedgerFuelMath.Interval interval:result.intervals)values.add(interval.litersPer100Km);
        return new Economy(result.distanceKm,result.consumedLiters,values);
    }

}
