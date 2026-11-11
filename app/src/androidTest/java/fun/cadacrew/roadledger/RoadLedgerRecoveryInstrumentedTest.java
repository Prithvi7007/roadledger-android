package fun.cadacrew.roadledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Synthetic fixtures ONLY; all tests use a dedicated test database. */
@RunWith(AndroidJUnit4.class)
public final class RoadLedgerRecoveryInstrumentedTest {
    private static final String TEST_DB = "roadledger-v031-instrumentation-test.db";
    private static final String[] TABLES = {"vehicles", "fuel", "expenses", "trips", "import_vehicle_links", "imported_rows"};
    private static final String[] ORDER_BY = {"id", "id", "id", "id", "source_key", "row_key"};
    private Context context;
    private GarageDb db;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DB); // Never touches roadledger.db.
        db = new GarageDb(context, TEST_DB);
        db.getWritableDatabase();
    }

    @After public void tearDown() {
        if (db != null) db.close();
        if (context != null) context.deleteDatabase(TEST_DB);
    }

    private long seedAllTables() {
        long vehicle = db.addVehicle("Synthetic Rider", "Test", "Model");
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.execSQL("UPDATE vehicles SET unit_system=1 WHERE id=?", new Object[]{vehicle});
        db.addFuel(vehicle, 1700000000123L, 1000.0, 40.5, 140.75, true);
        long second = db.addFuel(vehicle, 1700600000456L, 1400.0, 42.25, 151.10, true);
        sql.execSQL("UPDATE fuel SET missed_fillup=1 WHERE id=?", new Object[]{second});
        db.addExpense(vehicle, 1699000000000L, "Maintenance", 42.79, 1250.0, "Synthetic repair");
        db.addTrip(vehicle, 1700700000789L, 1400.0, 1460.4, "Synthetic trip");
        sql.execSQL("INSERT INTO import_vehicle_links(source_key,vehicle_id) VALUES(?,?)",
                new Object[]{"fixture-vehicle-key", vehicle});
        sql.execSQL("INSERT INTO imported_rows(row_key,record_kind,vehicle_key,imported_at) VALUES(?,?,?,?)",
                new Object[]{"fixture-fill-key", "fuel", "fixture-vehicle-key", 1700900000123L});
        return vehicle;
    }

    private byte[] backup() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RoadLedgerBackup.write(db, out);
        return out.toByteArray();
    }

    private String databaseState() {
        SQLiteDatabase sql = db.getReadableDatabase();
        StringBuilder all = new StringBuilder();
        for (int t=0;t<TABLES.length;t++) {
            all.append(TABLES[t]).append(':');
            try (Cursor c=sql.rawQuery("SELECT * FROM " + TABLES[t] + " ORDER BY " + ORDER_BY[t], null)) {
                while (c.moveToNext()) {
                    all.append('[');
                    for (int i=0;i<c.getColumnCount();i++) {
                        all.append(c.getColumnName(i)).append('=');
                        switch(c.getType(i)) {
                            case Cursor.FIELD_TYPE_NULL: all.append("N;");break;
                            case Cursor.FIELD_TYPE_INTEGER: all.append('I').append(c.getLong(i)).append(';');break;
                            case Cursor.FIELD_TYPE_FLOAT: all.append('F').append(Double.doubleToLongBits(c.getDouble(i))).append(';');break;
                            case Cursor.FIELD_TYPE_STRING:
                                String s=c.getString(i);
                                all.append('S').append(s.length()).append(':').append(s).append(';');break;
                            default: throw new AssertionError("Unexpected SQLite column type");
                        }
                    }
                    all.append(']');
                }
            }
            all.append('\n');
        }
        return all.toString();
    }

    @Test public void fullRoundTripPreservesAllTablesAndProvenance() throws Exception {
        long vehicle=seedAllTables();
        String expected=databaseState();
        byte[] bytes=backup();
        RoadLedgerBackup.Snapshot snapshot=RoadLedgerBackup.read(new ByteArrayInputStream(bytes));
        assertEquals(1,snapshot.count("vehicles"));
        assertEquals(2,snapshot.count("fuel"));
        assertEquals(1,snapshot.count("import_vehicle_links"));
        assertEquals(1,snapshot.count("imported_rows"));
        db.deleteVehicle(vehicle);
        db.addVehicle("Not in the backup", "", "");
        assertFalse(expected.equals(databaseState()));
        RoadLedgerBackup.restore(db,snapshot);
        assertEquals("Backup must exactly recover all normalized rows, IDs and timestamps",expected,databaseState());
    }

    @Test public void checksumMismatchIsRejectedWithoutTouchingRecords() throws Exception {
        seedAllTables();
        String before=databaseState();
        byte[] tampered=rewriteBackup(backup(),false,false);
        try {
            RoadLedgerBackup.read(new ByteArrayInputStream(tampered));
            fail("A bad checksum must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("checksum"));
        }
        assertEquals("Rejected archives cannot alter local records",before,databaseState());
    }

    @Test public void foreignKeyFailureRollsBackEntireRestore() throws Exception {
        seedAllTables();
        byte[] corrupted= rewriteBackup(backup(),true,true);
        RoadLedgerBackup.Snapshot poisoned=RoadLedgerBackup.read(new ByteArrayInputStream(corrupted));
        db.addVehicle("Keep current vehicle", "", "");
        String before=databaseState();
        try {
            RoadLedgerBackup.restore(db,poisoned);
            fail("An orphaned provenance link must make restore fail");
        } catch(IOException expected) {
            // Transaction must roll back the preceding deletes and inserts.
        }
        assertEquals("Restore failure must leave existing data untouched",before,databaseState());
    }

    @Test public void editsPersistAndFuelEconomyIsRecomputed() throws Exception {
        long vehicle=db.addVehicle("Before", "Old", "Car");
        long first=db.addFuel(vehicle,1700000000000L,1000,35,120,true);
        long second=db.addFuel(vehicle,1700600000000L,1400,40,135,true);
        long expense=db.addExpense(vehicle,1700100000000L,"Other",4,1100,"old");
        long trip=db.addTrip(vehicle,1700200000000L,1200,1250,"old");
        assertEquals(10.0,db.economy(vehicle).litersPer100Km,0.000001);
        db.updateVehicle(vehicle,"After","New","Vehicle");
        db.updateFuel(vehicle,second,1690000000000L,1500,40,135,true,false);
        db.updateExpense(vehicle,expense,1690000000000L,"Maintenance",88.50,1300,"edited");
        db.updateTrip(vehicle,trip,1690000000000L,1200,1300,"edited");
        assertEquals("After",db.vehicles().get(0).name);
        assertEquals(8.0,db.economy(vehicle).litersPer100Km,0.000001);
        assertEquals("edited",db.expenses(vehicle).get(0).note);
        assertEquals("edited",db.trips(vehicle).get(0).note);
        assertEquals(1690000000000L,db.fuel(vehicle).get(1).date);
        assertTrue(first>0);
    }

    private static byte[] zipEntry(byte[] backup,String wanted) throws Exception {
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(backup))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(wanted.equals(entry.getName())) {
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    byte[] buf=new byte[4096];int n;
                    while((n=zip.read(buf))>=0)if(n>0)out.write(buf,0,n);
                    return out.toByteArray();
                }
                zip.closeEntry();
            }
        }
        throw new IOException("Missing fixture entry: "+wanted);
    }

    /** Rebuilds a valid ZIP; optionally re-signs modified JSON with the correct SHA-256. */
    private static byte[] rewriteBackup(byte[] original,boolean resign,boolean badForeignKey) throws Exception {
        JSONObject manifest=new JSONObject(new String(zipEntry(original,"manifest.json"),StandardCharsets.UTF_8));
        JSONObject records=new JSONObject(new String(zipEntry(original,"records.json"),StandardCharsets.UTF_8));
        if(badForeignKey) {
            records.getJSONArray("import_vehicle_links").getJSONObject(0).put("vehicle_id",99999999L);
        } else {
            records.getJSONArray("vehicles").getJSONObject(0).put("name","Tampered fixture vehicle");
        }
        byte[] content=records.toString().getBytes(StandardCharsets.UTF_8);
        if(resign)manifest.put("records_sha256",sha256(content));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("records.json"));
            zip.write(content);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String sha256(byte[] contents) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        StringBuilder s=new StringBuilder();
        for(byte b:md.digest(contents))s.append(String.format(Locale.US,"%02x",b&0xff));
        return s.toString();
    }
}
