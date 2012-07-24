package com.joebateson.hallApp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.SwitchPreference;
import android.util.Log;

public class PrefsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    
    private static final String[] DAYS = {"hallTypeMonday", "hallTypeTuesday", "hallTypeWednesday",
                                          "hallTypeThursday", "hallTypeFriday", "hallTypeSaturday", "hallTypeSunday"};
    
    private static final String HALL_TYPE = "hallType";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
        Log.i("PREFS_LOL", "onCreate()");
    }
    
    @Override
    protected void onResume(){
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(this);
        Log.i("PREFS_LOL", "onResume()");
        
        updatePreference(HALL_TYPE);
        for (String day : DAYS) {
            updatePreference(day);
        }
        
        
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
            .unregisterOnSharedPreferenceChangeListener(this);
        Log.i("PREFS_LOL", "onPause()");
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        updatePreference(key);
    }
    
    private void updatePreference(String key){
        Preference preference = findPreference(key);
        if (preference instanceof ListPreference){
            ListPreference listPreference = (ListPreference)preference; 
            listPreference.setSummary(listPreference.getEntry());
            if (key.equals(HALL_TYPE)) {
                boolean enabled = listPreference.getValue().equals("advanced") ? true : false;
                    for (String day : DAYS) {
                        Preference preferenceDay = findPreference(day);
                        ListPreference listPreferenceDay = (ListPreference)preferenceDay;
                        listPreferenceDay.setEnabled(enabled);
                    }
            }           
            
        } else if (key.equals("autoHall")) {
            Log.i("AUTOHALLKJAWDJUAWD", "autohall KEY CHANGED");
            
            boolean enabled = false;
            if (preference instanceof CheckBoxPreference) {
                CheckBoxPreference pref = (CheckBoxPreference)preference;
                enabled = pref.isChecked();
            }  
            else if (preference instanceof SwitchPreference) {
                SwitchPreference pref = (SwitchPreference)preference;
                enabled = pref.isChecked();
            }
            
            Log.i("new autohall value", "" + enabled);
            
            if (enabled) {
                Intent i = new Intent(this, HallService.class);
                startService(i);
            } else {
                stopService(new Intent(this, HallService.class));
            }
        }
    }

}
