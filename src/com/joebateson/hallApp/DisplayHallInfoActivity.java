package com.joebateson.hallApp;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

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
import org.json.JSONArray;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class DisplayHallInfoActivity extends Activity {
    
    public static final String TAG = "HallApp";
    
    private ListView lv;
    private MyListAdapter listAdapter;
    private ArrayList<String> values;
    private ArrayList<String> details;
    
    private Date selectedDay = null;
    private long selectedWordId = -1L;
    
    private static String mealBookingIndexHtml = "<h1>Default html</h1>";
    
    private static final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
    private static final SimpleDateFormat formatPretty = new SimpleDateFormat("EEEE, dd MMMM yyyy");
    
    private SharedPreferences globalSettings;
    private SharedPreferences.Editor globalSettingsEditor;
    private static HttpClient httpClient;
    private static HttpContext httpContext;
    private static CookieStore cookieStore;    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hall_list); 
        lv = (ListView) findViewById(R.id.lvResult);
        registerForContextMenu(lv);
        
        globalSettings = PreferenceManager.getDefaultSharedPreferences(this);
        globalSettingsEditor = globalSettings.edit();
        
        if (globalSettings.getBoolean("autoHall", false)) {
            Intent startServiceIntent = new Intent(this, HallService.class);
            startService(startServiceIntent);
        }
         
        updateValues();
        updateDetails();
        
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        String crsid = globalSettings.getString("crsid", null);
        String password = globalSettings.getString("password", null);
        
        if (crsid == null || password == null) {
            Context context = getApplicationContext();
            CharSequence text = "Please enter your CRSid and password";
            int duration = Toast.LENGTH_LONG;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            
            Intent intent = new Intent(this, PrefsActivity.class);
            startActivity(intent);
        } else {
            //new LoginTask().execute(crsid, password);
            //updateValues();
            //updateDetails();
        }
        
        
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        Adapter adapter = listAdapter;
        
        selectedDay = (Date) adapter.getItem(info.position);
        selectedWordId = info.id;        
        
        menu.setHeaderTitle(formatPretty.format(selectedDay));        
        
        menu.add(1, 1, 0, "Book first hall");
        menu.add(1, 2, 0, "Book formal hall");
        menu.add(1, 3, 0, "No hall");
    }
    
    @Override 
    public boolean onContextItemSelected(MenuItem item){ 
        switch (item.getItemId()) {
        case 1:
                putHallBooking(globalSettings, selectedDay, true, globalSettings.getBoolean("veggie", false));
                updateDetails();
                break;
        case 2: 
                putHallBooking(globalSettings, selectedDay, false, globalSettings.getBoolean("veggie", false));
                updateDetails();
                break;
        case 3: 
                cancelHallBooking(globalSettings, selectedDay);
                updateDetails();
                break;
        } 
        return false; 
    } 
    
    protected void toast(String message) {
        Context context = getApplicationContext();
        CharSequence text = message;
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
    
    private void updateValues() {
        values = new ArrayList<String>();        
        Calendar day = Calendar.getInstance();
        Date date = day.getTime();
        for (int i = 0; i<7; i++) {
            values.add(formatPretty.format(date));            
            day.roll(Calendar.DAY_OF_MONTH, 1);
            date = day.getTime();            
        }
        listAdapter = new MyListAdapter(DisplayHallInfoActivity.this, values, details);
        lv.setAdapter(listAdapter);
    }
    
    private void updateDetails() {
        details = new ArrayList<String>();
        Calendar day = Calendar.getInstance();
        Date date = day.getTime();
        for (int i = 0; i<7; i++) {
            details.add(getHallBooking(globalSettings, date));
            day.roll(Calendar.DAY_OF_MONTH, 1);
            date = day.getTime();           
        }
        listAdapter = new MyListAdapter(DisplayHallInfoActivity.this, values, details);
        lv.setAdapter(listAdapter);
    }
    
    private static void putHallBooking(SharedPreferences settings, Date day, boolean firstHall, boolean vegetarian) {
        //bookHall(null, firstHall, vegetarian);
        String veggie = vegetarian ? " - Vegetarian" : "";
        String hallType = firstHall ? "First Hall" + veggie : "Formal Hall" + veggie;
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(format.format(day), hallType);
        editor.commit();
    }
    
    private static String getHallBooking(SharedPreferences settings, Date day) {       
        return settings.getString(format.format(day), "No Hall");        
    }
    
    private static void cancelHallBooking(SharedPreferences settings, Date day) {
        String dayS = format.format(day);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove(dayS);
        editor.commit();
    }
    
    private ArrayList<String> jsonArrayToList(JSONArray jsonArray) {
        ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                list.add(jsonArray.getString(i));
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return list;
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
    
    
    private class LoginTask extends AsyncTask<String, Integer, String> {
        
        private ProgressDialog dialog;
        
        @Override
        protected String doInBackground(String... args) {           
            return login(args[0], args[1]);
        }
        
        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(DisplayHallInfoActivity.this, "", 
                    "Logging in to Caius Hall Booking", true);
        }
        
        
        @Override
        protected void onProgressUpdate(Integer... progress) {
        
        }

        @Override
        protected void onPostExecute(String result) {
            dialog.cancel();
            toast(result);
        }

        
    }
    
    
    private String login(String crsid, String password) {       
        
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
            Log.i(TAG, mealBookingIndexHtml);
            Document doc = Jsoup.parse(mealBookingIndexHtml);
            Element error = doc.select("span.error").first();
            if (error != null) {
                return error.text();
            } else {
                return "noRavenError";
            }
            
        } catch (ClientProtocolException e) {
            System.out.println("Something went wrong with ClientProtocol");
            return "ClientProtocol error";
        } catch (IOException e) {
            System.out.println("Something went wrong with IOException");
            return "IOException error";
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
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.items, menu);
        return true;
    }
    
    @Override 
    public boolean onOptionsItemSelected(MenuItem item){ 
        switch (item.getItemId()) {
        case R.id.menu_preferences:
                Intent intent = new Intent(this, PrefsActivity.class);
                startActivity(intent);
                break;
        case R.id.menu_refresh:
                String crsid = globalSettings.getString("crsid", "");
                String password = globalSettings.getString("password", "");
                new LoginTask().execute(crsid, password);
                updateValues();
                updateDetails();
        } 
        return false; 
    } 
}
