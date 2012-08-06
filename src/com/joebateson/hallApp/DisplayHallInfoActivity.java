package com.joebateson.hallApp;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    protected static final SimpleDateFormat formatPretty = new SimpleDateFormat("EEEE d MMMM yyyy");
    
    private SharedPreferences globalSettings;
    private SharedPreferences.Editor globalSettingsEditor;
    private static HttpClient httpClient;
    private static HttpContext httpContext;
    private static CookieStore cookieStore;
    private static boolean loggedIn = false;
    
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
        	new LoginTask().execute(crsid, password);
        }
        
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
        		new BookHallTask(selectedDay, true, globalSettings.getBoolean("veggie", false)).execute();                
                break;
        case 2: 
        		new BookHallTask(selectedDay, false, globalSettings.getBoolean("veggie", false)).execute();
                break;
        case 3: 
        		// TODO: cancel online as well as locally
                localCancelHallBooking(globalSettings, selectedDay);
                localUIUpdateBookingStatus();
                break;
        } 
        return false; 
    } 
    
    protected void localUIToast(String message) {
        Context context = getApplicationContext();
        CharSequence text = message;
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
    
    private void localUIUpdateDatesShown() {
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
    
    private void localUIUpdateBookingStatus() {
        details = new ArrayList<String>();
        Calendar day = Calendar.getInstance();
        Date date = day.getTime();
        for (int i = 0; i<7; i++) {
            details.add(localGetHallBooking(globalSettings, date));
            day.roll(Calendar.DAY_OF_MONTH, 1);
            date = day.getTime();           
        }
        listAdapter = new MyListAdapter(DisplayHallInfoActivity.this, values, details);
        lv.setAdapter(listAdapter);
    }
    
    private class BookHallTask extends AsyncTask<String, Void, Boolean> {
    	
    	private Date day;
    	private boolean firstHall;
    	private boolean vegetarian;
    	
    	protected BookHallTask(Date day, boolean firstHall, boolean veggie){
    		this.day = day;
    		this.firstHall = firstHall;
    		this.vegetarian = veggie;
    	}
    	
		@Override
		protected Boolean doInBackground(String... params) {
			
			return netBookHall(day, firstHall, vegetarian);
		}

		@Override
		protected void onPreExecute() {
			// TODO Auto-generated method stub
			super.onPreExecute();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (!result){
				localUIToast("Something went wrong");
				return;
			} else {
				localPutHallBooking(globalSettings, day, firstHall, vegetarian);
	    		localUIUpdateBookingStatus();
			}   		
		}
        
        

        
    }
    
    private static boolean netBookHall(Date date, boolean firstHall, boolean vegetarian) {
    	
    	if (!loggedIn){
    		return false;
    	}
    	
    	
        int year = date.getYear();
        String month = new String[] { "01", "02", "03", "04","05", "06", "07", "08", "09", "10", "11", "12" } [ date.getMonth() ];
        int day = date.getDay();
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
            netPostData(url, nameValuePairs);
            return true;
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }          
    }
    
    private static String[] localPutHallBooking(SharedPreferences settings, Date day, boolean firstHall, boolean vegetarian) {
        String veggie = vegetarian ? " - Vegetarian" : "";
        String hallType = firstHall ? "First Hall" + veggie : "Formal Hall" + veggie;
        String dayString = format.format(day);
        SharedPreferences.Editor editor = settings.edit();
        
        editor.putString(dayString, hallType);
        editor.commit();
        
        String[] result = new String[2];
        result[0] = dayString;
        result[1] = hallType;
        
        return result;
    }
    
    private static String localGetHallBooking(SharedPreferences settings, Date day) {       
        return settings.getString(format.format(day), "No Hall");        
    }
    
    private static void localCancelHallBooking(SharedPreferences settings, Date day) {
        String dayS = format.format(day);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove(dayS);
        editor.commit();
    }
    
    public String netGetData(String url) throws ClientProtocolException, IOException {
        HttpGet get = new HttpGet(url);
        HttpResponse resp = httpClient.execute(get, httpContext);
        HttpEntity entity = resp.getEntity();
        return EntityUtils.toString(entity, HTTP.UTF_8);
    } 
    
    public static String netPostData(String url, List<NameValuePair> nameValuePairs) throws ClientProtocolException, IOException {
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
        	String result = netLogin(args[0], args[1]);
        	try {
				netPullBookings(mealBookingIndexHtml);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	
            return result;
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
        	localUIUpdateDatesShown();
            localUIUpdateBookingStatus();
            dialog.cancel();
            localUIToast(result);
        }

        
    }
     
    private String netLogin(String crsid, String password) {       
        
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
            Document doc = Jsoup.parse(mealBookingIndexHtml);
            Element error = doc.select("span.error").first();
            if (error != null) {
                return error.text();
            } else {
            	loggedIn = true;
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
    
    private boolean netPullBookings(String html) throws IOException, ParseException{
    	
    	if (!loggedIn){
    		return false;
    	}
    	
    	
    	
    	//When system is live use real base
    	//String base = "https://www.cai.cam.ac.uk/mealbookings/";
    	String base = "http://192.168.0.9:8888/";
    	//Document doc = Jsoup.parse(html);
    	Document doc = Jsoup.connect(base).get();
    	String linkSelector = "table.list:eq(3) a:contains(View/Edit) ";
    	Elements links = doc.select(linkSelector);
    	
    	ArrayList<String> bookingDates = new ArrayList<String>();
    	
    	for (Element link : links){
    		Document page = Jsoup.connect(base + link.attr("href")).get();

    		String date = page.select("table.list td:contains(Date) ~ td").first().text();
    		Date theDate = formatPretty.parse(date);
    		Log.i("hallbooking", "found elements");
    		Log.i("hallbooking", localGetHallBooking(globalSettings, theDate));
    		String hallType = page.select("h1").first().text();
    		Boolean firstHall = hallType.indexOf("First") != -1;
    		Boolean veggie = Integer.parseInt(page.select("table.list td:contains(Vegetarians) ~ td").first().text()) > 0;
    		String vString = veggie ? " - Vegetarian" : "";
    		
    		bookingDates.add(localPutHallBooking(globalSettings, theDate, firstHall, veggie)[0]);
    	}
    	
    	//clear all local bookings that were not on the server
    	Set<String> allSettingKeys = globalSettings.getAll().keySet();
    	
    	
    	for (String key : allSettingKeys){
    		if (key.indexOf("20") != -1 && !(bookingDates.contains(key))){
    			globalSettingsEditor.remove(key);
    		}
    	}
    	globalSettingsEditor.commit();
    	
    	return true;
    	
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
        case R.id.menu_print_settings:
        		Log.i("GLOBALSETTINGS", globalSettings.getAll().toString());
        		Log.i("DEV", httpContext.toString());
        		Log.i("DEV", httpClient.toString());
        } 
        return false; 
    } 
}
