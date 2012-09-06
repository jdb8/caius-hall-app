package com.joebateson.CaiusHall;

import java.io.IOException;
import java.security.KeyStore;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.Toast;

public class DisplayHallInfoActivity extends Activity {
    
    public static final String TAG = "CaiusHall";
    
    private ListView lv;
    private MyListAdapter listAdapter;
    private ArrayList<String> values;
    private ArrayList<String> details;
    
    private Date selectedDay = null;
    private String baseURL = "http://192.168.0.9:8888/";
    private static String mealBookingIndexHtml = "<h1>Default html</h1>";
    
    private static final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
    protected static final SimpleDateFormat formatPretty = new SimpleDateFormat("EEEE d MMMM yyyy");
    
    private SharedPreferences globalSettings;
    private SharedPreferences.Editor globalSettingsEditor;
    private static HttpClient httpClient;
    private static HttpContext httpContext;
    private static CookieStore cookieStore;
    private static boolean loggedIn = false;
    
    private ProgressDialog globalDialog;

	private static ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>();
	
	
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        final Object[] data = new Object[5];
        data[0] = httpClient;
        data[1] = httpContext;
        data[2] = cookieStore;
        return data;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hall_list); 
        
        lv = (ListView) findViewById(R.id.lvResult);
        registerForContextMenu(lv);
        
        globalSettings = PreferenceManager.getDefaultSharedPreferences(this);
        globalSettingsEditor = globalSettings.edit();
             
        final Object[] data = (Object[]) getLastNonConfigurationInstance();
        if (data != null) {
            httpClient = (HttpClient) data[0];
            httpContext = (HttpContext) data[1];
            cookieStore = (CookieStore) data[2];
            localUIUpdateDatesShown();
            localUIUpdateBookingStatus();
        } else {
        	
            if (globalSettings.getBoolean("autoHall", false)) {
                Intent startServiceIntent = new Intent(this, AutoHallActiveService.class);
                startService(startServiceIntent);
            }
            
//            String crsid = globalSettings.getString("crsid", null);
//            String password = globalSettings.getString("password", null);
            
//            if (crsid == null || password == null) {
//                Context context = getApplicationContext();
//                CharSequence text = "Please enter your CRSid and password";
//                int duration = Toast.LENGTH_LONG;
//
//                Toast toast = Toast.makeText(context, text, duration);
//                toast.show();
//                
//                Intent intent = new Intent(this, PrefsActivity.class);
//                startActivity(intent);
//            } else {
//            	//new LoginAndPullTask().execute(crsid, password);
//            }
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
        } else if (!loggedIn) {
        	new LoginAndPullTask().execute(crsid, password);
        } else {
        	localUIUpdateDatesShown();
            localUIUpdateBookingStatus();
        }
        
        
    }
    
    /* (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		
		for (AsyncTask task : tasks){
			task.cancel(true);
		}
		
		if (globalDialog != null) {
			globalDialog.dismiss();
		}
		
	}
	
	
	

	@Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        Adapter adapter = listAdapter;
        
        selectedDay = (Date) adapter.getItem(info.position);
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
                //localCancelHallBooking(globalSettings, selectedDay);
        		localUIToast("Cancelling bookings is not implemented yet - sorry!");
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
			DisplayHallInfoActivity.tasks.add(this);
            globalDialog = ProgressDialog.show(DisplayHallInfoActivity.this, "", 
                    "Booking...", true);
        }

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (!result){
				localUIToast("Something went wrong in BookHallTask");
			} else {
				localPutHallBooking(globalSettings, day, firstHall, vegetarian);
	    		localUIUpdateBookingStatus();
			} 
			globalDialog.dismiss();
			DisplayHallInfoActivity.tasks.remove(this);
		}
        
        

        
    }
    
    protected static boolean netBookHall(Date date, boolean firstHall, boolean vegetarian) {
    	
    	if (!loggedIn){
    		return false;
    	}
    	
    	Calendar calendar = Calendar.getInstance();
    	calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        String month = new String[] { "01", "02", "03", "04","05", "06", "07", "08", "09", "10", "11", "12" } [ calendar.get(Calendar.MONTH) ];
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
            netPostData(url, nameValuePairs);
            Log.i("DisplayHallInfoActivity", "Booked hall on " + date.toString());
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
    
    protected static String[] localPutHallBooking(SharedPreferences settings, Date day, boolean firstHall, boolean vegetarian) {
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
    
    protected static void localCancelHallBooking(SharedPreferences settings, Date day) {
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
    
    private class LoginAndPullTask extends AsyncTask<String, Integer, String> {
        
        @Override
        protected String doInBackground(String... args) {
        	String result = netLogin(args[0], args[1]);
        	
        	
            return result;
        }
        
        @Override
        protected void onPreExecute() {
        	DisplayHallInfoActivity.tasks.add(this);
            globalDialog = ProgressDialog.show(DisplayHallInfoActivity.this, "", 
                    "Logging in to Caius Hall Booking", true);
        }
        
        
        @Override
        protected void onProgressUpdate(Integer... progress) {
        
        }

        @Override
        protected void onPostExecute(String result) {
            globalDialog.dismiss();
            DisplayHallInfoActivity.tasks.remove(this);
            new PullBookingsTask().execute(baseURL);
            localUIToast(result);
        }

        
    }
    
    public HttpClient getNewHttpClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new InsecureDebugSSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            return new DefaultHttpClient();
        }
    }
     
    private String netLogin(String crsid, String password) {       
        
        try {
        	// TODO: Change this back when Caius hall site renews SSL!
            // httpClient = new DefaultHttpClient();
        	Log.e("INSECURE WARNING", "CURRENTLY IGNORING SSL CERTIFICATE VALIDITY USING A HODGEPODGE PIECE OF CODE, CHANGE BACK");
        	httpClient = getNewHttpClient();
            httpContext = new BasicHttpContext();
            cookieStore = new BasicCookieStore();
            httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
            
            HttpGet method = new HttpGet("https://www.cai.cam.ac.uk/mealbookings/index.php");
            HttpResponse rp = httpClient.execute(method, httpContext);
            
            EntityUtils.toString(rp.getEntity(), HTTP.UTF_8);
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
                return "Successfully logged in";
            }
            
        } catch (ClientProtocolException e) {
            System.out.println("Something went wrong - ClientProtocol");
            return "ClientProtocol error";
        } catch (IOException e) {
            System.out.println("Something went wrong - IOException");
            e.printStackTrace();
            return "IOException error";
        }               
        
    }

    private class PullBookingsTask extends AsyncTask<String, Integer, Boolean> {
    	
		@Override
		protected Boolean doInBackground(String... params) {
			
			try {
				return netPullBookings(params[0]);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
		}

		@Override
        protected void onPreExecute() {
			DisplayHallInfoActivity.tasks.add(this);
            globalDialog = ProgressDialog.show(DisplayHallInfoActivity.this, "", 
                    "Fetching bookings from server", true);
        }

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (!result){
				localUIToast("Something went wrong in onPostExecute");
			} else {
				localUIUpdateDatesShown();
    			localUIUpdateBookingStatus();
			}
			DisplayHallInfoActivity.tasks.remove(this);
			globalDialog.dismiss();
		}
        

        
    }
    
    private boolean netPullBookings(String url) throws IOException, ParseException{
    	
    	if (!loggedIn){
    		return false;
    	}
    	
    	
    	
    	//When system is live use real base
    	//String base = "https://www.cai.cam.ac.uk/mealbookings/";
    	
    	//Document doc = Jsoup.parse(html);
    	Document doc = Jsoup.connect(url).get();
    	
    	if (doc == null) {
    		return false;
    	} else {
    		String linkSelector = "table.list:eq(3) a:contains(View/Edit) ";
        	Elements links = doc.select(linkSelector);
        	
        	ArrayList<String> bookingDates = new ArrayList<String>();
        	
        	for (Element link : links){
        		Document page = Jsoup.connect(url + link.attr("href")).get();
        		if (page == null) {
        			return false;
        		} else {
        			String date = page.select("table.list td:contains(Date) ~ td").first().text();
            		Date theDate = formatPretty.parse(date);
            		String hallType = page.select("h1").first().text();
            		Boolean firstHall = hallType.indexOf("First") != -1;
            		Boolean veggie = Integer.parseInt(page.select("table.list td:contains(Vegetarians) ~ td").first().text()) > 0;
            		bookingDates.add(localPutHallBooking(globalSettings, theDate, firstHall, veggie)[0]);
        		}
        		
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
    	
    	
    	
    }   
    
    protected static Date futureDay(int requiredDay) {
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
        return requiredDate.getTime();
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
        		if (!loggedIn){
        			String crsid = globalSettings.getString("crsid", "");
                    String password = globalSettings.getString("password", "");
                    new LoginAndPullTask().execute(crsid, password);
        		} else {
        			new PullBookingsTask().execute(baseURL);        			
        		}
                
        case R.id.menu_print_settings:
        		Log.i("GLOBALSETTINGS", globalSettings.getAll().toString());
        		Log.i("DEV", httpContext.toString());
        		Log.i("DEV", httpClient.getParams().toString());
        		Log.i("DEV", cookieStore.getCookies().toString());
        } 
        return false; 
    } 
}
