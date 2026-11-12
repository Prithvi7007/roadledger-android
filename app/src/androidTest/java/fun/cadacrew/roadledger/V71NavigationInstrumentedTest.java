package fun.cadacrew.roadledger;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Executes only on the disposable CI emulator; test data is synthetic and removed. */
@RunWith(AndroidJUnit4.class)
public final class V71NavigationInstrumentedTest {
    private static View named(View view,String description) {
        CharSequence name=view.getContentDescription();
        if(name!=null&&name.toString().equals(description))return view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View match=named(group.getChildAt(i),description);
                if(match!=null)return match;
            }
        }
        return null;
    }
    private static boolean containsText(View view,String expected){
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(expected))return true;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(containsText(group.getChildAt(i),expected))return true;
        }
        return false;
    }
    private static int primaryTabs(View view){
        int total=0;CharSequence description=view.getContentDescription();
        if(description!=null&&description.toString().matches("(Home|Fuel|Trips) tab(?:, selected)?"))total++;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)total+=primaryTabs(group.getChildAt(i));
        }
        return total;
    }
    @Test public void threeTabsHomeAnalyticsAndDirectFuelAction(){
        final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        GarageDb db=new GarageDb(context);
        // Safety: do not run this test against an existing vehicle database.
        assertTrue("Only use fresh disposable emulator",db.vehicles().isEmpty());
        long id=db.addVehicle("SYNTHETIC NAV TEST","Test","Vehicle");
        Activity activity=null;
        try{
            Intent intent=new Intent(context,V7Activity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final Activity open=activity;
            View root=activity.findViewById(android.R.id.content);
            assertEquals("Three primary destinations",3,primaryTabs(root));
            assertTrue(containsText(root,"Your overview"));
            assertTrue(containsText(root,"SPENDING TREND"));
            assertTrue(containsText(root,"Add Fill-up"));
            assertNotNull(named(root,"More vehicle tools"));
            assertNotNull(named(root,"Switch vehicle"));
            View fuel=named(root,"Fuel tab");
            assertNotNull(fuel);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(fuel::performClick);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue(containsText(open.findViewById(android.R.id.content),"Fuel & energy"));
            View trips=named(open.findViewById(android.R.id.content),"Trips tab");
            assertNotNull(trips);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(trips::performClick);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue(containsText(open.findViewById(android.R.id.content),"My trips"));
            View home=named(open.findViewById(android.R.id.content),"Home tab");
            assertNotNull(home);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(home::performClick);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue(containsText(open.findViewById(android.R.id.content),"Add Fill-up"));
        }finally{
            if(activity!=null){Activity finish=activity;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(finish::finish);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
            db.deleteVehicle(id);db.close();
        }
    }
}
