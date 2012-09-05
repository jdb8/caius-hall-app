package com.joebateson.hallApp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class HallAutoBookReceiver extends BroadcastReceiver {

    private static final String DEBUG_TAG = "HallAutoBookReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(DEBUG_TAG, "Recurring alarm; requesting booking service.");
        Intent booker = new Intent(context, HallBookService.class);
        context.startService(booker);
    }
}
