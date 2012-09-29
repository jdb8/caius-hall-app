package com.joebateson.CaiusHall;

import java.util.Calendar;
import java.util.TimeZone;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class AutoHallActiveService extends Service {

    private boolean isActive = false;

    protected void setRecurringAlarm(Context context) {

        Calendar updateTime = Calendar.getInstance();
        updateTime.add(Calendar.DAY_OF_MONTH, 1);
        updateTime.setTimeZone(TimeZone.getTimeZone("GMT"));
        updateTime.set(Calendar.HOUR_OF_DAY, 10);
        updateTime.set(Calendar.MINUTE, 02);

        Intent booker = new Intent(context, HallAutoBookReceiver.class);
        booker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent recurringBooking = PendingIntent.getBroadcast(context, 0, booker, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, updateTime.getTimeInMillis(), AlarmManager.INTERVAL_DAY, recurringBooking);
        Log.i("HALLSERVICE", "set recurring alarm");
    }

    private void cancelRecurringAlarm(Context context) {
        Intent booker = new Intent(context, HallAutoBookReceiver.class);
        booker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent recurringBooking = PendingIntent.getBroadcast(context, 0, booker, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        alarms.cancel(recurringBooking);
        Log.i("HALLSERVICE", "cancel recurring alarm");
    }

    private void run() {
        if (!isActive) {
          isActive = true;

          Notification note = new Notification(R.drawable.ic_stat_calendar, "Automatic booking active", System.currentTimeMillis());
          Intent i = new Intent(this, DisplayHallInfoActivity.class);

          i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

          PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

          note.setLatestEventInfo(this, "Caius Hall Booker", "Automatic booking active", pi);
          note.flags |= Notification.FLAG_NO_CLEAR;

          setRecurringAlarm(getApplicationContext());

          startForeground(1337, note);
        }
    }

    private void stop() {
        if (isActive) {
          isActive = false;
          cancelRecurringAlarm(getApplicationContext());
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
