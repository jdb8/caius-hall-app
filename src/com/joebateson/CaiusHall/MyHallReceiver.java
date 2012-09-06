package com.joebateson.CaiusHall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class MyHallReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        if (settings.getBoolean("autoHall", false)) {
            Intent startServiceIntent = new Intent(context, AutoHallActiveService.class);
            context.startService(startServiceIntent);
        }        
    }
}
