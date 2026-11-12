package fun.cadacrew.roadledger;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

/** Opt-in foreground GPS-only recording. Never launches itself from Bluetooth or on boot. */
public final class V7TripService extends Service implements LocationListener {
    public static final String START="fun.cadacrew.roadledger.V7TripService.START";
    public static final String STOP="fun.cadacrew.roadledger.V7TripService.STOP";
    public static final String VEHICLE="vehicle";
    private static final String CHANNEL="roadledger_location_v7";
    private static final int NOTIFICATION_ID=7001;
    private GarageDb db;private V7Store extra;private LocationManager manager;
    private long vehicleId=0,routeId=0;
    private double startKm=0;
    private Location previous;
    private double sumKm=0;
    @Override public void onCreate(){super.onCreate();db=new GarageDb(this);extra=new V7Store(db);manager=(LocationManager)getSystemService(LOCATION_SERVICE);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        long v=intent.getLongExtra(VEHICLE,0);
        if(STOP.equals(intent.getAction())){finish(v);return START_NOT_STICKY;}
        if(!START.equals(intent.getAction())||v<=0){stopSelf();return START_NOT_STICKY;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
        if(routeId!=0){return START_NOT_STICKY;}
        vehicleId=v;
        try{
            startKm=db.currentOdometerKm(v);
            routeId=extra.activeRoute(v);
            if(routeId==0)routeId=extra.beginRoute(v,System.currentTimeMillis(),intent.getStringExtra("purpose"));
            createNotificationChannel();
            Notification notice=notice("Recording your trip · Tap RoadLedger to stop");
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTIFICATION_ID,notice);
            // Previous points are also persisted, so a killed process can recover distance.
            java.util.List<V7Store.Point> past=extra.points(routeId);
            if(!past.isEmpty()){
                V7Store.Point last=past.get(past.size()-1);
                previous=new Location(LocationManager.GPS_PROVIDER);
                previous.setLatitude(last.lat);previous.setLongitude(last.lon);
                sumKm=distanceKm(past);
            }
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000,10,this);
        }catch(RuntimeException ex){finish(v);return START_NOT_STICKY;}
        return START_NOT_STICKY;
    }
    @Override public void onLocationChanged(Location p){
        if(routeId==0||p==null||p.getAccuracy()>150||p.getAccuracy()<0)return;
        if(previous!=null){
            float meters=previous.distanceTo(p);
            if(meters<8||meters>2000)return;
            sumKm+=meters/1000d;
        }
        try{
            extra.point(routeId,p.getTime()>0?p.getTime():System.currentTimeMillis(),p.getLatitude(),p.getLongitude(),
                    p.hasAltitude()?p.getAltitude():0,p.getAccuracy(),p.hasSpeed()?p.getSpeed():0);
            previous=new Location(p);
        }catch(RuntimeException ignored){ /* Database error must not leak location into logs. */ }
    }
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    private static double distanceKm(java.util.List<V7Store.Point> pts){
        double km=0;
        for(int i=1;i<pts.size();i++){
            V7Store.Point a=pts.get(i-1),b=pts.get(i);
            Location x=new Location("record");x.setLatitude(a.lat);x.setLongitude(a.lon);
            Location y=new Location("record");y.setLatitude(b.lat);y.setLongitude(b.lon);
            float meters=x.distanceTo(y);
            if(meters>=8&&meters<=2000)km+=meters/1000d;
        }return km;
    }
    private void finish(long requestedVehicle){
        long id=routeId,vehicle=requestedVehicle>0?requestedVehicle:vehicleId;
        if(id==0&&vehicle>0)id=extra.activeRoute(vehicle);
        try{
            if(manager!=null)manager.removeUpdates(this);
            if(id>0){
                java.util.List<V7Store.Point> pts=extra.points(id);
                double km=distanceKm(pts);
                long trip=0;
                if(km>0.02&&vehicle>0){
                    double initial=startKm>0?startKm:db.currentOdometerKm(vehicle);
                    trip=db.addTrip(vehicle,System.currentTimeMillis(),initial,initial+km,"GPS recorded trip");
                }
                extra.finishRoute(id,System.currentTimeMillis(),km,trip);
            }
        }finally{
            routeId=0;vehicleId=0;previous=null;
            stopForeground(true);stopSelf();
        }
    }
    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"Trip recording",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Shown only when an opt-in GPS trip is being recorded");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }
    private Notification notice(String text){
        Intent i=new Intent(this,V7Activity.class);
        PendingIntent pending=PendingIntent.getActivity(this,7,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_roadledger).setContentTitle("RoadLedger · GPS trip")
                .setContentText(text).setContentIntent(pending).setOngoing(true).build();
    }
    @Override public void onDestroy(){if(manager!=null)manager.removeUpdates(this);if(db!=null)db.close();super.onDestroy();}
}
