package com.joebateson.CaiusHall;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.widget.Toast;

public class PrefsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    
    private static final String[] DAYS = {"hallTypeMonday", "hallTypeTuesday", "hallTypeWednesday",
                                          "hallTypeThursday", "hallTypeFriday", "hallTypeSaturday", "hallTypeSunday"};
    
    private static final String HALL_TYPE = "hallType";

	public ProgressDialog globalDialog;
	private Preference updateCheckPref;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
    }
    
    @Override
    protected void onResume(){
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        
        // Listen for update check click
        updateCheckPref = (Preference) findPreference("updateCheck");
        updateCheckPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
            	new CheckForUpdateTask().execute();
				return false;
            }
        });
        
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
        
        // Unregister click listener
        updateCheckPref.setOnPreferenceClickListener(null);
        
        if (globalDialog != null) {
			globalDialog.dismiss();
		}
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        updatePreference(key);
    }
    
    
    
    @SuppressLint({ "NewApi", "NewApi" })
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
            
            boolean enabled = false;
            if (preference instanceof CheckBoxPreference) {
                CheckBoxPreference pref = (CheckBoxPreference)preference;
                enabled = pref.isChecked();
            }  
            else if (preference instanceof SwitchPreference) {
                SwitchPreference pref = (SwitchPreference)preference;
                enabled = pref.isChecked();
            }
            
            if (enabled) {
                Intent i = new Intent(this, AutoHallActiveService.class);
                startService(i);
            } else {
                stopService(new Intent(this, AutoHallActiveService.class));
            }
        }
    }
    
    private class CheckForUpdateTask extends AsyncTask<String, Integer, String> {
    	
		@Override
		protected String doInBackground(String... params) {
			String currentRevision = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("app_revision", null);
			HttpClient client = new DefaultHttpClient();
			HttpGet get = new HttpGet("http://jdb75.user.srcf.net/latestAPK.txt");
			try {
				HttpResponse response = client.execute(get);
				HttpEntity entity = response.getEntity();
		        String latestVersion = EntityUtils.toString(entity, HTTP.UTF_8);
		        if (currentRevision.equals(latestVersion.trim())) {
		        	// We have latest version, return null
		        	return null;
		        } else {
		        	// Return the required version revision
		        	return latestVersion;
		        }
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}

		@Override
        protected void onPreExecute() {
            globalDialog = ProgressDialog.show(PrefsActivity.this, "", "Checking for update", true);
        }

		@Override
		protected void onPostExecute(String revision) {
			super.onPostExecute(revision);
			globalDialog.dismiss();
			if (revision != null) {
				new DownloadLatestUpdateTask(revision).execute();
			} else {
				Toast.makeText(getApplicationContext(), "You already have the latest version", Toast.LENGTH_LONG).show();
			}
		}
        

        
    }
    
    private class DownloadLatestUpdateTask extends AsyncTask<String, Integer, Boolean> {
    	
    	String revision;
    	String filename;
    	File location = new File(Environment.getExternalStorageDirectory() + "/download");
    	
    	public DownloadLatestUpdateTask(String revision){
    		this.revision = revision.trim();
    	}
    	
		@Override
		protected Boolean doInBackground(String... params) {
			
			try {
				Boolean isSDPresent = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
				if (!isSDPresent){
					return false;
				} else {
					this.filename = "CaiusHall-debug-" + revision + ".apk";
					File testFile = new File(location, filename);
					if (testFile.exists()) {
						// Don't bother downloading the file again, but return true to install it
						return true;
					} else {
						// Get the file
						URL url = new URL("http://jdb75.user.srcf.net/apks/" + filename);
			            HttpURLConnection c = (HttpURLConnection) url.openConnection();
			            c.setRequestMethod("GET");
			            c.setDoOutput(true);
			            c.connect();
			            
			            // Check the length for the progress dialog
			            int fileLength = c.getContentLength();
			            
			            // Put it somewhere useful
			            String PATH = location.toString();
			            File file = new File(PATH);
			            file.mkdirs();
			            File outputFile = new File(file, filename);
			            FileOutputStream fos = new FileOutputStream(outputFile);
			            
			            InputStream is = c.getInputStream();
			            byte[] buffer = new byte[1024];
			            int len1 = 0;
			            long total = 0;
			            while ((len1 = is.read(buffer)) != -1) {
			            	total += len1;
			            	publishProgress((int) (total * 100 / fileLength));
			                fos.write(buffer, 0, len1);
			            }
			            fos.close();
			            is.close();
			            
						return true;
					}
				}
				
				
			
			} catch(Exception e) {
				e.printStackTrace();
				return false;
			}
			
		}
		
		@Override
	    protected void onProgressUpdate(Integer... progress) {
	        super.onProgressUpdate(progress);
	        globalDialog.setProgress(progress[0]);
	    }

		@Override
        protected void onPreExecute() {
			super.onPreExecute();
			globalDialog = new ProgressDialog(PrefsActivity.this);
			globalDialog.setMessage("Downloading update");
			globalDialog.setIndeterminate(false);
			globalDialog.setMax(100);
			globalDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			globalDialog.show();
        }

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			globalDialog.dismiss();
			
			if (result) {
				Toast.makeText(getApplicationContext(), "Installing update", Toast.LENGTH_LONG).show();
				Intent intent = new Intent(Intent.ACTION_VIEW);
				File file = new File(location + "/" + filename);
	            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
	            startActivity(intent);
			} else {
				Toast.makeText(getApplicationContext(), "There was an update, but something went wrong while downloading it. Try downloading manually " +
						"from the GitHub page", Toast.LENGTH_LONG).show();
			}
			
		}
        

        
    }

}
