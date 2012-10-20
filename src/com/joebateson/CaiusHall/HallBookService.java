package com.joebateson.CaiusHall;

import java.util.Calendar;
import java.util.Date;
import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseArray;

public class HallBookService extends IntentService {
    
    private GoogleAnalyticsTracker tracker;

    public HallBookService() {
        super("HallBookService");       
    }

    private class BookAllDesiredHallsTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... strings) {

            if (!DisplayHallInfoActivity.netIsLoggedIn()){
                DisplayHallInfoActivity.netLogin();
            }

            if (!DisplayHallInfoActivity.netIsLoggedIn()){
                Log.e("Login error", "Should be logged in, something wrong (HallBookService)");
                return false;
            }

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean veggie = settings.getBoolean("veggie", false);

            int[] days = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};

            SparseArray<String> dayTypes = new SparseArray<String>(7);

            String daySetting = settings.getString("hallType", "undefined");
            if (daySetting.equals("advanced")){
                dayTypes.put(days[0], settings.getString("hallTypeMonday", "undefined"));
                dayTypes.put(days[1], settings.getString("hallTypeTuesday", "undefined"));
                dayTypes.put(days[2], settings.getString("hallTypeWednesday", "undefined"));
                dayTypes.put(days[3], settings.getString("hallTypeThursday", "undefined"));
                dayTypes.put(days[4], settings.getString("hallTypeFriday", "undefined"));
                dayTypes.put(days[5], settings.getString("hallTypeSaturday", "undefined"));
                dayTypes.put(days[6], settings.getString("hallTypeSunday", "undefined"));
            } else if (daySetting.equals("alwaysFirst")){
                for (int day : days){
                    dayTypes.put(day, "first");
                }
            } else if (daySetting.equals("alwaysFormal")) {
                for (int day : days){
                    dayTypes.put(day, "formal");
                }
            }
            
            Calendar cal = Calendar.getInstance();
            Date theDay;
            int weekday;
            String action;
            boolean returnStatus;
            int failures = 0;
            // Iterate through each day, starting with today, and perform the relevant action
            for (int i = 0; i < 5; i++) {
                weekday = cal.get(Calendar.DAY_OF_WEEK);
                action = dayTypes.get(weekday);
                theDay = cal.getTime();                
                try {                    
                    if (action.equals("first")){
                        DisplayHallInfoActivity.netBookHall(theDay, true, veggie);
                        returnStatus = DisplayHallInfoActivity.netPullOneBooking(theDay);
                        if (returnStatus) tracker.trackEvent("Application events", "Auto booking", "First/Cafeteria", 0);
                    } else if (action.equals("formal")){
                        DisplayHallInfoActivity.netBookHall(theDay, false, veggie);
                        returnStatus = DisplayHallInfoActivity.netPullOneBooking(theDay);
                        if (returnStatus) tracker.trackEvent("Application events", "Auto booking", "Formal", 0);
                    } else if (action.equals("noHall")){
                        DisplayHallInfoActivity.netCancelHall(theDay);
                        // should be false since we cancelled it! If false set returnStatus to true
                        returnStatus = !DisplayHallInfoActivity.netPullOneBooking(theDay);
                        if (returnStatus) tracker.trackEvent("Application events", "Auto cancellation", "", 0);
                    } else {
                        // A setting was not set properly?
                        Log.e("CaiusHall", "Autohall day property not set for " + theDay.toString());
                        returnStatus = false;
                    }
                    
                } catch (Exception e) {
                    returnStatus = false;
                }
                                
                // If return status is false, increase the failure count by 1
                failures += (returnStatus) ? 0 : 1;
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            
            tracker.dispatch();
            tracker.stopSession();
            // allow for one failure, either the first or the last day
            return (failures <= 1);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            
            // Post a notification letting the user know the autobooker has done its work
            String content = result ? "Success!" : "There were one or more errors";
            Notification noti = new NotificationCompat.Builder(getApplicationContext())
            .setContentTitle("Caius Hall booked")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_tick)
            .build();

            NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(8008135, noti);
        }
    }   
    

    @Override
    protected void onHandleIntent(Intent intent) {
        tracker = GoogleAnalyticsTracker.getInstance();
        tracker.startNewSession("UA-35696884-1", this); 
        new BookAllDesiredHallsTask().execute(); 
    }
}
