package fun.cadacrew.roadledger;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Isolated synthetic database only; never reads user roadledger.db. */
@RunWith(AndroidJUnit4.class)
public final class CoreExperienceInstrumentedTest {
    private static final String FILE="roadledger-v04-only-test.db";
    private Context ctx;
    private GarageDb db;
    @Before public void before(){ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();ctx.deleteDatabase(FILE);db=new GarageDb(ctx,FILE);db.getWritableDatabase();}
    @After public void after(){if(db!=null)db.close();if(ctx!=null)ctx.deleteDatabase(FILE);}

    @Test public void stationsPersistFillupsAndDeletionPreservesFuel(){
        long v=db.addVehicle("Synthetic car","Test","A");
        long f=db.addFuel(v,1700000000000L,2100,35.5,100,true);
        long station=db.saveStation(v,0,"Test station","100 Test St");
        db.setFuelStation(v,f,station);
        assertEquals("Test station",db.stationName(db.fuel(v).get(0).stationId));
        assertEquals("100 Test St",db.stations(v).get(0).address);
        db.deleteStation(v,station);
        assertEquals(1,db.fuel(v).size());
        assertEquals(0,db.fuel(v).get(0).stationId);
        assertTrue(db.stations(v).isEmpty());
    }
    @Test public void maintenanceDeadlinesAndCompletionWork(){
        long v=db.addVehicle("Synthetic car","Test","A");
        db.addFuel(v,1700000000000L,2600,35,100,true);
        long now=System.currentTimeMillis();
        long overdue=db.saveService(v,0,"Overdue oil",now-86400000L,0,"",false);
        long soon=db.saveService(v,0,"Tires",0,3100,"",false);
        long later=db.saveService(v,0,"Long term",now+90L*86400000L,9000,"",false);
        assertEquals(3,db.services(v).size());
        assertEquals(1,db.overdueServices(v,now));
        GarageDb.Service tire=null, oil=null, future=null;
        for(GarageDb.Service s:db.services(v)){
            if(s.id==soon)tire=s;
            if(s.id==overdue)oil=s;
            if(s.id==later)future=s;
        }
        assertNotNull(tire);assertNotNull(oil);assertNotNull(future);
        assertEquals(1,tire.priority(now,2600));
        assertEquals(0,oil.priority(now,2600));
        assertEquals(2,future.priority(now,2600));
        db.saveService(v,overdue,"Overdue oil",now-86400000L,0,"done",true);
        assertEquals(0,db.overdueServices(v,now));
    }
    @Test public void customCategoriesAndInsightsAggregateActualCosts(){
        long v=db.addVehicle("Synthetic car","Test","A");
        long now=System.currentTimeMillis();
        db.addCategory(v,"Car wash");db.addCategory(v,"Car wash");
        assertEquals(1,db.customCategories(v).size());
        db.addFuel(v,now-86400000L,1000,30,120,true);
        db.addExpense(v,now-86400000L,"Car wash",25,1000,"fixture");
        db.addExpense(v,now-86400000L,"Parking",5,1000,"fixture");
        RoadLedgerAnalytics.Snapshot s=RoadLedgerAnalytics.compute(db.fuel(v),db.expenses(v),now);
        assertEquals(120,s.totalFuelCost,.00001);assertEquals(30,s.totalExpenseCost,.00001);
        assertEquals(150,s.totalCost,.00001);
        assertEquals(4,s.averagePricePerLiter,.00001);
        assertEquals(150,s.last12MonthsCost,.00001);
        assertEquals(25,s.costByCategory.get("Car wash"),.00001);
        double total=0;for(double m:s.sixMonthSpend)total+=m;
        assertEquals(150,total,.00001);
        db.deleteCategory(v,"Car wash");
        assertTrue(db.customCategories(v).isEmpty());
        assertEquals("Car wash",db.expenses(v).get(1).category);
    }
    @Test public void allV3ProductDataRoundTripsThroughNativeBackup() throws Exception {
        long v=db.addVehicle("Synthetic car","Test","A");
        long f=db.addFuel(v,1700000000000L,1000,30,120,true);
        long station=db.saveStation(v,0,"Town pumps","Town road");
        db.setFuelStation(v,f,station);
        db.addCategory(v,"Detailing");
        db.saveService(v,0,"Service",1700800000000L,3000,"Filter",false);
        ByteArrayOutputStream output=new ByteArrayOutputStream();RoadLedgerBackup.write(db,output);
        RoadLedgerBackup.Snapshot snap=RoadLedgerBackup.read(new ByteArrayInputStream(output.toByteArray()));
        assertEquals(1,snap.count("service_items"));assertEquals(1,snap.count("fuel_stations"));
        assertEquals(1,snap.count("expense_categories"));
        db.deleteVehicle(v);
        RoadLedgerBackup.restore(db,snap);
        long restored=db.vehicles().get(0).id;
        assertEquals(1,db.services(restored).size());
        assertEquals("Town pumps",db.stationName(db.fuel(restored).get(0).stationId));
        assertEquals("Detailing",db.customCategories(restored).get(0));
    }
    @Test public void priorV2BackupRestoresWithSaneDefaults() throws Exception {
        long v=db.addVehicle("Legacy fixture","Test","Car");
        db.addFuel(v,1700000000000L,1000,30,100,true);
        ByteArrayOutputStream out=new ByteArrayOutputStream();RoadLedgerBackup.write(db,out);
        byte[] old=makeV2Backup(out.toByteArray());
        RoadLedgerBackup.Snapshot prior=RoadLedgerBackup.read(new ByteArrayInputStream(old));
        assertEquals(1,prior.count("fuel"));assertEquals(0,prior.count("service_items"));
        db.deleteVehicle(v);
        RoadLedgerBackup.restore(db,prior);
        long id=db.vehicles().get(0).id;
        assertEquals(0,db.fuel(id).get(0).stationId);
    }
    @Test public void sqliteV2UpgradePreservesVehicleAndFuel() {
        db.close();db=null;ctx.deleteDatabase(FILE);
        SQLiteDatabase sql=SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(FILE),null);
        sql.execSQL("CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, make TEXT NOT NULL DEFAULT '', model TEXT NOT NULL DEFAULT '', unit_system INTEGER NOT NULL DEFAULT 0)");
        sql.execSQL("CREATE TABLE fuel (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE, date INTEGER NOT NULL, odometer REAL NOT NULL, liters REAL NOT NULL, cost REAL NOT NULL, full_tank INTEGER NOT NULL, missed_fillup INTEGER NOT NULL DEFAULT 0)");
        sql.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE, date INTEGER NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, odometer REAL NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '')");
        sql.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE, date INTEGER NOT NULL, start_km REAL NOT NULL, end_km REAL NOT NULL, note TEXT NOT NULL DEFAULT '')");
        sql.execSQL("CREATE TABLE import_vehicle_links (source_key TEXT PRIMARY KEY, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE)");
        sql.execSQL("CREATE TABLE imported_rows (row_key TEXT PRIMARY KEY, record_kind TEXT NOT NULL, vehicle_key TEXT NOT NULL, imported_at INTEGER NOT NULL)");
        sql.execSQL("INSERT INTO vehicles(id,name,make,model,unit_system) VALUES(17,'Legacy','Test','Car',1)");
        sql.execSQL("INSERT INTO fuel(vehicle_id,date,odometer,liters,cost,full_tank,missed_fillup) VALUES(17,1700000000000,1000,30,110,1,0)");
        sql.setVersion(2);sql.close();
        db=new GarageDb(ctx,FILE);
        assertEquals(17,db.vehicles().get(0).id);
        assertEquals(1,db.fuel(17).size());
        assertEquals(0,db.fuel(17).get(0).stationId);
        assertTrue(db.services(17).isEmpty());
    }
    private static byte[] makeV2Backup(byte[] v3) throws Exception {
        JSONObject manifest=null, records=null;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(v3))){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] b=new byte[4096];int read;
                while((read=zip.read(b))!=-1)out.write(b,0,read);
                if(entry.getName().equals("manifest.json"))manifest=new JSONObject(out.toString("UTF-8"));
                if(entry.getName().equals("records.json"))records=new JSONObject(out.toString("UTF-8"));
            }
        }
        assertNotNull(manifest);assertNotNull(records);
        records.remove("fuel_stations");records.remove("service_items");records.remove("expense_categories");
        JSONArray fuel=records.getJSONArray("fuel");
        for(int i=0;i<fuel.length();i++)fuel.getJSONObject(i).remove("station_id");
        byte[] body=records.toString().getBytes(StandardCharsets.UTF_8);
        manifest.put("schema_version",2);
        byte[] sha=MessageDigest.getInstance("SHA-256").digest(body);
        StringBuilder hash=new StringBuilder();for(byte b:sha)hash.append(String.format(java.util.Locale.US,"%02x",b&255));
        manifest.put("records_sha256",hash.toString());
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("records.json"));zip.write(body);zip.closeEntry();
        }return out.toByteArray();
    }
}
