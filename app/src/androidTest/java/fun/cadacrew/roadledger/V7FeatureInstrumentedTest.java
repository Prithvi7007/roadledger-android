package fun.cadacrew.roadledger;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** v0.7 tests use isolated synthetic records only; never read private RoadLedger data. */
@RunWith(AndroidJUnit4.class)
public final class V7FeatureInstrumentedTest {
    private static final String NAME="roadledger-v07-fixtures-only.db";
    private Context context;private GarageDb db;private V7Store features;private long vehicle;
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase(NAME);db=new GarageDb(context,NAME);features=new V7Store(db);vehicle=db.addVehicle("Synthetic V7","Test","Car");}
    @After public void cleanup(){if(db!=null)db.close();if(context!=null)context.deleteDatabase(NAME);}

    @Test public void logsServiceAndCreatesNextDueReminder(){
        long time=1700000000000L;
        features.logAndSchedule(vehicle,time,"Synthetic Oil","Test Shop",25000,45,"SYNTHETIC",180,8000);
        assertEquals(1,features.serviceHistory(vehicle).size());
        assertEquals(1,db.services(vehicle).size());
        GarageDb.Service first=db.services(vehicle).get(0);
        assertEquals(time+180L*86400000L,first.dueDate);
        assertEquals(33000,first.dueKm,0.001);
        features.completeReminder(vehicle,first,time+180L*86400000L,33000);
        assertEquals(2,db.services(vehicle).size());
        assertEquals(1,db.services(vehicle).stream().filter(s->!s.completed).count());
        assertEquals(1,db.services(vehicle).stream().filter(s->s.completed).count());
    }
    @Test public void routesAndAttachmentsSurviveBackupRestore()throws Exception{
        long route=features.beginRoute(vehicle,1700000000000L,"Business");
        features.point(route,1700000000000L,28.5,-81.3,10,5,15);
        features.point(route,1700000005000L,28.6,-81.4,11,5,14);
        features.finishRoute(route,1700000010000L,14.5,0);
        assertEquals(2,features.points(route).size());
        assertEquals(14.5,features.routes(vehicle).get(0).distanceKm,0.0001);
        byte[] photo=new byte[]{1,4,9,2,8};
        long photoId=features.attach(vehicle,"Receipt",0,"synthetic.png","image/png",photo,"");
        features.setOcr(vehicle,photoId,"TOTAL 40.00");
        assertArrayEquals(photo,features.attachmentBytes(vehicle,photoId));
        long station=db.saveStation(vehicle,0,"Test Station","Synthetic Street");
        features.stationMeta(station,true,5,28.5,-81.2,3.49,1700000000000L);
        features.saveSettings(vehicle,"USD",7,150,false,"","Business");
        ByteArrayOutputStream out=new ByteArrayOutputStream();RoadLedgerBackup.write(db,out);
        RoadLedgerBackup.Snapshot snapshot=RoadLedgerBackup.read(new ByteArrayInputStream(out.toByteArray()));
        db.deleteVehicle(vehicle);
        assertTrue(db.vehicles().isEmpty());
        RoadLedgerBackup.restore(db,snapshot);
        assertEquals(2,features.points(route).size());
        assertArrayEquals(photo,features.attachmentBytes(vehicle,photoId));
        assertEquals("TOTAL 40.00",features.attachments(vehicle).get(0).ocr);
        assertEquals(5,features.stationMeta(station).rating);
        assertEquals("Business",features.settings(vehicle).purpose);
        assertEquals("Test Station",db.stationName(station));
    }
    @Test public void rejectsInvalidInputsWithoutWriting(){
        assertEquals(0,features.serviceHistory(vehicle).size());
        try{features.logAndSchedule(vehicle,1,"", "",0,0,"",0,0);fail();}catch(IllegalArgumentException expected){}
        try{features.attach(vehicle,"Receipt",0,"bad.png","image/png",new byte[0],"");fail();}catch(IllegalArgumentException expected){}
        try{features.point(12,1,1000,0,0,0,0);fail();}catch(IllegalArgumentException expected){}
        assertEquals(0,features.serviceHistory(vehicle).size());
        assertEquals(0,features.attachments(vehicle).size());
    }
    @Test public void settingsAndReminderWindows(){
        features.saveSettings(vehicle,"USD",3,120,true,"","Medical");
        assertEquals(3,features.settings(vehicle).warningDays);
        assertEquals("Medical",features.settings(vehicle).purpose);
        assertTrue(features.settings(vehicle).muted);
        assertTrue(V7Logic.reminderDue(1_000_000L,1_000_000L+2*86400000L,0,0,3,120));
        assertFalse(V7Logic.reminderDue(1_000_000L,1_000_000L+9*86400000L,0,0,3,120));
    }
    @Test public void installedDatabaseIncludesNewTables(){
        SQLiteDatabase raw=db.getReadableDatabase();
        for(String name:new String[]{"service_history","v7_vehicle_settings","v7_routes","v7_route_points","v7_attachments","v7_station_meta","v7_reminder_recurrence"}){
            try(android.database.Cursor c=raw.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",new String[]{name})){assertTrue(c.moveToFirst());assertEquals(name,1,c.getInt(0));}
        }
        assertEquals(6,raw.getVersion());
    }
}
