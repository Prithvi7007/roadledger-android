package fun.cadacrew.roadledger;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Only synthetic data in an isolated database; never accesses user records. */
@RunWith(AndroidJUnit4.class)
public final class VehicleUnitsInstrumentedTest {
    private static final String TEST_DB="roadledger-v06-fixtures-only.db";
    private Context context;
    private GarageDb db;
    @Before public void before(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DB);
        db=new GarageDb(context,TEST_DB);
    }
    @After public void after(){if(db!=null)db.close();if(context!=null)context.deleteDatabase(TEST_DB);}

    @Test public void quantityConversionsAndEnergyFamilies(){
        assertEquals(3.785411784,FuelUnits.toBase(1,"US gal"),0.00000001);
        assertEquals(4.54609,FuelUnits.toBase(1,"UK gal"),0.00000001);
        assertEquals(10,FuelUnits.toBase(10,"kWh"),0.00001);
        assertEquals(10,FuelUnits.toBase(10,"kg"),0.00001);
        assertEquals(10,FuelUnits.toBase(10,"m3"),0.00001);
        assertEquals(23.5214583,FuelUnits.displayConsumption(10,"L","US MPG",1),0.00001);
        assertEquals(28.2480936,FuelUnits.displayConsumption(10,"L","UK MPG",1),0.00001);
        assertEquals("kWh/100 km",FuelUnits.consumptionUnit("kWh","US MPG",1));
        try{FuelUnits.checkFuelUnit("Electric","US gal");fail("Electricity is not gallons");}catch(IllegalArgumentException expected){}
    }

    @Test public void preferencesBackupAndDualTankSurviveRestore() throws Exception {
        long id=db.addVehicle("Synthetic","Test","Hybrid");
        db.updateVehiclePreferences(id,2022,"SYNTHETICVIN","TEST-01","Synthetic insurer",1,
                "Gasoline","US gal",40,"CNG","kg",10,"US MPG");
        GarageDb.Vehicle v=db.vehicles().get(0);
        assertEquals(2022,v.year);assertEquals("SYNTHETICVIN",v.vin);
        assertEquals("CNG",v.tankFuel(2));assertEquals("kg",v.tankUnit(2));
        long f1=db.saveFuelEntry(id,0,1700000000000L,1000,FuelUnits.toBase(10,"US gal"),35,true,false,1,0,"US gal","",0,"Sunny");
        long f2=db.saveFuelEntry(id,0,1700000000100L,1000,8,25,true,false,2,0,"kg","",0,"Cloudy");
        assertEquals(2,db.fuel(id).size());
        assertEquals(8,db.fuel(id).get(0).liters,0.0001);
        assertEquals("kg",db.fuel(id).get(0).sourceUnit);
        ByteArrayOutputStream stream=new ByteArrayOutputStream();RoadLedgerBackup.write(db,stream);
        RoadLedgerBackup.Snapshot backup=RoadLedgerBackup.read(new ByteArrayInputStream(stream.toByteArray()));
        db.deleteVehicle(id);RoadLedgerBackup.restore(db,backup);
        assertEquals("CNG",db.vehicles().get(0).tankFuel(2));
        assertEquals(2,db.fuel(id).size());
        assertEquals(f1,db.fuel(id).get(1).id);
        assertEquals(f2,db.fuel(id).get(0).id);
        assertEquals("Cloudy",db.fuel(id).get(0).weather);
    }

    @Test public void protectsAgainstIncompatibleTankUnitChanges(){
        long id=db.addVehicle("Fixture","Test","Gas");
        db.saveFuelEntry(id,0,1700000000000L,1000,20,50,true,false,1,0,"L","",0,"");
        try{
            db.updateVehiclePreferences(id,0,"","","",0,"Electric","kWh",0,"Disabled","L",0,"Auto");
            fail("Cannot reinterpret stored litres as kWh");
        }catch(IllegalArgumentException expected){}
        assertEquals("Gasoline",db.vehicles().get(0).tankFuel(1));
    }

    @Test public void v4DatabaseUpgradesWithoutDestroyingRecords(){
        db.close();db=null;context.deleteDatabase(TEST_DB);
        SQLiteDatabase sql=SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB),null);
        sql.execSQL("CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, make TEXT NOT NULL DEFAULT '', model TEXT NOT NULL DEFAULT '', unit_system INTEGER NOT NULL DEFAULT 0)");
        sql.execSQL("CREATE TABLE fuel (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL, date INTEGER NOT NULL, odometer REAL NOT NULL, liters REAL NOT NULL, cost REAL NOT NULL, full_tank INTEGER NOT NULL, missed_fillup INTEGER NOT NULL DEFAULT 0, station_id INTEGER NOT NULL DEFAULT 0, tank_number INTEGER NOT NULL DEFAULT 1)");
        sql.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT,vehicle_id INTEGER,date INTEGER,category TEXT,amount REAL,odometer REAL,note TEXT)");
        sql.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT,vehicle_id INTEGER,date INTEGER,start_km REAL,end_km REAL,note TEXT)");
        sql.execSQL("CREATE TABLE import_vehicle_links (source_key TEXT PRIMARY KEY, vehicle_id INTEGER)");
        sql.execSQL("CREATE TABLE imported_rows (row_key TEXT PRIMARY KEY, record_kind TEXT,vehicle_key TEXT,imported_at INTEGER)");
        sql.execSQL("CREATE TABLE fuel_stations (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER,name TEXT,address TEXT)");
        sql.execSQL("CREATE TABLE service_items (id INTEGER PRIMARY KEY AUTOINCREMENT,vehicle_id INTEGER,title TEXT,due_date INTEGER,due_km REAL,note TEXT,completed INTEGER)");
        sql.execSQL("CREATE TABLE expense_categories (id INTEGER PRIMARY KEY AUTOINCREMENT,vehicle_id INTEGER,name TEXT)");
        sql.execSQL("CREATE TABLE fuelio_archives (sha256 TEXT PRIMARY KEY,source_name TEXT,payload_base64 TEXT,imported_at INTEGER)");
        sql.execSQL("CREATE TABLE import_diagnostics (id INTEGER PRIMARY KEY AUTOINCREMENT,archive_sha256 TEXT,row_number INTEGER,section TEXT,message TEXT)");
        sql.execSQL("INSERT INTO vehicles (id,name,make,model,unit_system) VALUES (10,'Preserved','Test','Car',1)");
        sql.execSQL("INSERT INTO fuel(vehicle_id,date,odometer,liters,cost,full_tank,missed_fillup,station_id,tank_number) VALUES (10,1700000000000,3000,30,40,1,0,0,2)");
        sql.setVersion(4);sql.close();
        db=new GarageDb(context,TEST_DB);
        assertEquals(10,db.vehicles().get(0).id);
        assertEquals("US gal",db.vehicles().get(0).tankUnit(1));
        assertEquals("Gasoline",db.vehicles().get(0).tankFuel(2));
        assertEquals(1,db.fuel(10).size());
        assertEquals("L",db.fuel(10).get(0).sourceUnit);
    }
}
