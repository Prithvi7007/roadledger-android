package fun.cadacrew.roadledger;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Private, daily best-effort reminder scheduling with per-vehicle warning/mute settings. */
public final class V7ReminderWorker extends Worker {
    public V7ReminderWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    public static void schedule(Context app){
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(V7ReminderWorker.class,1,TimeUnit.DAYS).build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("roadledger-service-reminders",ExistingPeriodicWorkPolicy.KEEP,request);
    }
    @NonNull @Override public Result doWork(){
        Context context=getApplicationContext();
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return Result.success();
        try(GarageDb db=new GarageDb(context)){
            V7Store store=new V7Store(db);long now=System.currentTimeMillis();
            NotificationManager mgr=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel("roadledger_reminders_v7","Vehicle maintenance",NotificationManager.IMPORTANCE_DEFAULT);mgr.createNotificationChannel(channel);}
            for(GarageDb.Vehicle v:db.vehicles()){
                V7Store.Settings settings=store.settings(v.id);
                if(settings.muted)continue;
                for(GarageDb.Service item:db.services(v.id)){
                    if(item.completed||!V7Logic.reminderDue(now,item.dueDate,db.currentOdometerKm(v.id),item.dueKm,settings.warningDays,settings.warningKm))continue;
                    // Stable per-service ID prevents a new notification every daily run.
                    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,"roadledger_reminders_v7"):new Notification.Builder(context);
                    Notification notice=b.setSmallIcon(R.drawable.ic_roadledger).setContentTitle("Maintenance · "+v.name)
                            .setContentText(item.title+" is due or approaching its deadline")
                            .setAutoCancel(true).build();
                    mgr.notify((int)(item.id%Integer.MAX_VALUE),notice);
                }
            }
            return Result.success();
        }catch(RuntimeException e){return Result.retry();}
    }
}
