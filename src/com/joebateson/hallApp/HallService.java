package com.joebateson.hallApp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class HallService extends Service {
    
    private boolean isActive = false;
    
    @SuppressWarnings("deprecation")
    private void run() {
        if (!isActive) {
          isActive=true;

          Notification note=new Notification(R.drawable.ic_stat_calendar,
                                              "Automatic booking active",
                                              System.currentTimeMillis());
          Intent i=new Intent(this, DisplayHallInfoActivity.class);
        
          i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|
                     Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
          PendingIntent pi=PendingIntent.getActivity(this, 0,
                                                      i, 0);
          
          note.setLatestEventInfo(this, "Caius Hall Booker",
                                  "Automatic booking active",
                                  pi);
          note.flags|=Notification.FLAG_NO_CLEAR;

          startForeground(1337, note);
        }
    }
    
    private void stop() {
        if (isActive) {
          isActive=false;
          stopForeground(true);
        }
      }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onDestroy() {
        stop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        run();
        
        return START_NOT_STICKY;

    }

}
