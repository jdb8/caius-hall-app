package com.joebateson.hallApp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public class HallAppActivity extends Activity {
    
    public static final String TAG = "HallApp";
    public static final String EXTRA_MESSAGE = "com.joebateson.hallApp";
    //public static final String PREFS_NAME = "HallAppPrefs";
    
    private SharedPreferences settings;
    private static HttpClient httpClient;
    private static HttpContext httpContext;
    private static CookieStore cookieStore;
    
    private static String mealBookingIndexHtml = "<h1>Default html</h1>";
    
    public void resetSettings(View view) {
        SharedPreferences.Editor settingsEditor = settings.edit();
        settingsEditor.clear();
        settingsEditor.commit();
    }
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        Log.w(TAG, "settings at creation of main activity: " + settings.getAll().toString());
        
        
        
        Log.i(TAG, "onCreate()");
        
        if (settings.getString("crsid", "") != "") {
            //login(settings.getString("crsid", ""), settings.getString("password", ""));
            Log.w(TAG, "settings: " + settings.getAll().toString());
            Intent intent = new Intent(this, DisplayHallInfoActivity.class);
            intent.putExtra(EXTRA_MESSAGE, parse(mealBookingIndexHtml));
            startActivity(intent);
        } else {            
            Intent intent = new Intent(this, PrefsActivity.class);
            startActivity(intent);
        }        
        
        
    }   

    public String getData(String url) throws ClientProtocolException, IOException {
        HttpGet get = new HttpGet(url);
        HttpResponse resp = httpClient.execute(get, httpContext);
        HttpEntity entity = resp.getEntity();
        return EntityUtils.toString(entity, HTTP.UTF_8);
    } 
    
    public static String postData(String url, List<NameValuePair> nameValuePairs) throws ClientProtocolException, IOException {
        HttpPost post = new HttpPost(url);
        post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
        
        HttpResponse resp = httpClient.execute(post, httpContext);
        HttpEntity entity = resp.getEntity();
        return EntityUtils.toString(entity, HTTP.UTF_8);
    } 
    
    private static void login(String crsid, String password) {       
        
        try {
            httpClient = new DefaultHttpClient();
            httpContext = new BasicHttpContext();
            cookieStore = new BasicCookieStore();
            httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
            HttpGet method = new HttpGet("https://www.cai.cam.ac.uk/mealbookings/index.php");
            HttpResponse rp = httpClient.execute(method, httpContext);
            //need this to allow connection to close, don't remove
            String needThisHereForSillyReasons = EntityUtils.toString(rp.getEntity(), HTTP.UTF_8);
            String location = "https://raven.cam.ac.uk/auth/authenticate2.html?ver=1&url=https%3a%2f%2fwww.cai.cam.ac.uk%2fmealbookings%2findex.php";
            
            HttpPost post = new HttpPost(location);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(3);
            nameValuePairs.add(new BasicNameValuePair("userid", crsid));
            nameValuePairs.add(new BasicNameValuePair("pwd", password));
            nameValuePairs.add(new BasicNameValuePair("submit", "Submit"));
            post.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            HttpResponse response = httpClient.execute(post, httpContext);
            HttpEntity entity2 = response.getEntity();
            mealBookingIndexHtml = EntityUtils.toString(entity2, HTTP.UTF_8);            
            
        } catch (ClientProtocolException e) {
            System.out.println("Something went wrong with ClientProtocol");
        } catch (IOException e) {
            System.out.println("Something went wrong with IOException");            
        }               
        
    }
    
    private String parse(String html) {
        Document doc = Jsoup.parse(html);
        Elements h1 = doc.select("h1");
        Element h1Element = h1.first();
        String h1Text = h1Element.text();
        
        return h1Text;
    }
    
    private static void bookHall(Calendar calendar, boolean firstHall, boolean vegetarian) {
        int year = calendar.get(Calendar.YEAR);
        String month = new String[] { "01", "02", "03", "04","05", "06", "07", "08", "09", "10", "11", "12" } [ calendar.get( GregorianCalendar.MONTH ) ];
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String format = String.format("%%0%dd", 2);
        String sDay = String.format(format, day);        
        int hallCode = 140;
        String veggie = "0";
        if (!firstHall) hallCode = 141;
        if (vegetarian) veggie = "1";        
        String url = "https://www.cai.cam.ac.uk/mealbookings/index.php?event=" + hallCode + "&date=" + year + "-" + month + "-" + sDay;
        
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(5);
        nameValuePairs.add(new BasicNameValuePair("guests", "0"));
        nameValuePairs.add(new BasicNameValuePair("guests_names", ""));
        nameValuePairs.add(new BasicNameValuePair("vegetarians", veggie));
        nameValuePairs.add(new BasicNameValuePair("requirements", ""));
        nameValuePairs.add(new BasicNameValuePair("update", "Create"));
        
        try {
            String html = postData(url, nameValuePairs);
            Log.i(TAG, html);
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }          
    }   
    
    protected static Calendar futureDay(int requiredDay) {
        Calendar requiredDate = new GregorianCalendar();
        int today = requiredDate.get(Calendar.DAY_OF_WEEK);
        while (requiredDay != today) {
            requiredDate.add(Calendar.DAY_OF_WEEK, 1);
            today = requiredDate.get(Calendar.DAY_OF_WEEK);
        }
        requiredDate.set(Calendar.HOUR, 10);
        requiredDate.set(Calendar.MINUTE, 0);
        requiredDate.set(Calendar.SECOND, 0);
        requiredDate.set(Calendar.MILLISECOND, 0);
        return requiredDate;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(1, 1, 0, "Preferences");
        return true;
    }
    
    @Override 
    public boolean onOptionsItemSelected(MenuItem item){ 
        switch (item.getItemId()) { 
        case 1: 
                Log.i(TAG, "preferences was pressed lul");
                Intent intent = new Intent(this, PrefsActivity.class);
                startActivity(intent);
                break;
        } 
        return false; 
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
        Log.i(TAG, "onPause()");
    }

    @Override
    protected void onRestart() {
        // TODO Auto-generated method stub
        super.onRestart();
        Log.i(TAG, "onRestart()");
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        Log.i(TAG, "onResume()");
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        Log.i(TAG, "onStart()");
        //bookHall(futureDay(GregorianCalendar.WEDNESDAY), true, true);
    }    

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        super.onStop();
        Log.i(TAG, "onStop()");
    }
}