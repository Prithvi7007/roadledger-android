package fun.cadacrew.roadledger;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Simple offline vehicle dashboard widget; opens the redesigned RoadLedger launcher. */
public final class V7Widget extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        try(GarageDb db=new GarageDb(context)){
            GarageDb.Vehicle v=db.vehicles().isEmpty()?null:db.vehicles().get(0);
            for(int id:ids){
                RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.v7_widget);
                views.setTextViewText(R.id.v7_widget_name,v==null?"Add your first car":v.name);
                views.setTextViewText(R.id.v7_widget_odo,v==null?"RoadLedger":String.format(java.util.Locale.US,"%,.0f %s",db.currentOdometerKm(v.id)/(v.unitSystem==1?1.609344:1),v.unitSystem==1?"mi":"km"));
                views.setTextViewText(R.id.v7_widget_status,v==null?"Tap to get started":"Service due: "+db.overdueServices(v.id,System.currentTimeMillis()));
                Intent intent=new Intent(context,V7Activity.class);
                PendingIntent pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.v7_widget_body,pending);manager.updateAppWidget(id,views);
            }
        }
    }
}
