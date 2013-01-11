package com.joebateson.CaiusHall;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;
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
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
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
    
    // Less malicious than it sounds
    private static GoogleAnalyticsTracker tracker;

    public static final String TAG = "CaiusHall";

    private ListView lv;
    private MyListAdapter listAdapter;
    private ArrayList<String> values;
    private ArrayList<String> details;

    private Date selectedDay = null;
    private static final String baseURL = "https://www.cai.cam.ac.uk/mealbookings/";

    // For debugging purposes
    // private String baseURL = "http://192.168.0.9:8888";

    private static String mealBookingIndexHtml = "<h1>Default html</h1>";

    private static final SimpleDateFormat format = new SimpleDateFormat(
            "yyyyMMdd");
    protected static final SimpleDateFormat formatPretty = new SimpleDateFormat(
            "EEEE d MMMM yyyy");
    
    // Hall codes for first, formal, cafeteria (saturday first), sunday formal
    private static final int[] hallCodes = {200, 201, 206, 202};

    private static SharedPreferences globalSettings;
    private SharedPreferences.Editor globalSettingsEditor;
    private static HttpClient httpClient;
    private static HttpContext httpContext;
    private static CookieStore cookieStore;

    private ProgressDialog globalDialog;

    private static ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>();

    /**
     * Retrieves the 'version' number from the version.properties file.
     *
     * This function will fetch the git commit hash which is set during the build process in
     * res/raw/version.properties.
     *
     * @return String The string which refers to the current git commit.
     * @see <a href="http://www.tristanwaddington.com/2011/07/update-hg-or-git-changest-during-android-build/">http://www.tristanwaddington.com/2011/07/update-hg-or-git-changest-during-android-build/</a>
     */
    public String getAppChangsetFromPropertiesFile() {
        Resources resources = getResources();

        try {
            InputStream rawResource = resources.openRawResource(R.raw.version);
            Properties properties = new Properties();
            properties.load(rawResource);
            return properties.getProperty("changeset").trim();
        } catch (IOException e) {
            Log.e(TAG, "Cannot load app version properties file", e);
        }

        return null;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        final Object[] data = new Object[5];
        data[0] = httpClient;
        data[1] = httpContext;
        data[2] = cookieStore;
        return data;
    }

    /**
     * Function to check if the user is logged in.
     *
     * The function uses an HTTP get request to request a page only accessible
     * if logged in ({@link baseURL}), and then checks the location header. If not redirected to
     * Raven, the location header should be equal to the baseURL.
     *
     * @return true if user is logged in, false otherwise
     */
    protected static boolean netIsLoggedIn() {

        boolean answer = false;

        try {
            HttpGet get = new HttpGet(baseURL);
            HttpParams params = new BasicHttpParams();
            params.setParameter("http.protocol.handle-redirects",false);
            get.setParams(params);

            HttpResponse resp = httpClient.execute(get, httpContext);

            int statusCode = resp.getStatusLine().getStatusCode();
            answer = (statusCode == 200);

            resp.getEntity().consumeContent();
        } catch (ClientProtocolException e) {
            Log.e(TAG, "ClientProtocolException in netIsLoggedIn()");
            return false;
        } catch (IOException e) {
            return false;
        }

        return answer;
    }

    /**
     * A function to set up the required HttpClient and HttpContext.
     *
     * Assigns new values of the HttpClient and HttpContext to the fields in the activity.
     */
    private void setUpHttp(){
        if (httpClient == null){
            httpClient = new AdditionalCertHttpClient(getApplicationContext());
            httpContext = new BasicHttpContext();
            cookieStore = new BasicCookieStore();
            httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        tracker = GoogleAnalyticsTracker.getInstance();
        tracker.startNewSession("UA-35696884-1", this);
        
        tracker.trackPageView("/mainActivity");        
        tracker.dispatch();

        setContentView(R.layout.hall_list);

        lv = (ListView) findViewById(R.id.lvResult);
        registerForContextMenu(lv);

        globalSettings = PreferenceManager.getDefaultSharedPreferences(this);
        globalSettingsEditor = globalSettings.edit();

        globalSettingsEditor.putString("app_revision",
                this.getAppChangsetFromPropertiesFile());
        globalSettingsEditor.commit();

        final Object[] data = (Object[]) getLastNonConfigurationInstance();
        if (data != null) {
            httpClient = (HttpClient) data[0];
            httpContext = (HttpContext) data[1];
            cookieStore = (CookieStore) data[2];
            localUIUpdateDatesShown();
            localUIUpdateBookingStatus();
        } else {
            setUpHttp();
            if (globalSettings.getBoolean("autoHall", false)) {
                Intent startServiceIntent = new Intent(this,
                        AutoHallActiveService.class);
                startService(startServiceIntent);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
      super.onDestroy();
      // Stop the tracker when it is no longer needed.
      tracker.stopSession();
    }

    @Override
    public void onResume() {
        super.onResume();

        String crsid = globalSettings.getString("crsid", null);
        String password = globalSettings.getString("password", null);

        if (crsid == null || password == null) {

            localUIToast("Please enter your CRSid and password");

            Intent intent = new Intent(this, PrefsActivity.class);
            startActivity(intent);
        } else {
            
            localUIUpdateDatesShown();
            localUIUpdateBookingStatus();            
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();

        for (AsyncTask task : tasks) {
            task.cancel(true);
        }

        if (globalDialog != null) {
            globalDialog.dismiss();
        }

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        Adapter adapter = listAdapter;
        
        // Get the day that was selected, as a Date object
        selectedDay = (Date) adapter.getItem(info.position);
        
        // Set the heading for the menu
        menu.setHeaderTitle(formatPretty.format(selectedDay));
        
        // Initialise a calendar to extract the day
        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDay);
        
        // Check for saturday - if so, display an altered menu
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            menu.add(1, 1, 0, "Book cafeteria hall");
            menu.add(1, 3, 1, "No hall");
        } else {
            menu.add(1, 1, 0, "Book first hall");
            menu.add(1, 2, 0, "Book formal hall");
            menu.add(1, 3, 0, "No hall");
        }

        
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case 1:
            tracker.trackEvent("User actions", "Manual booking", "First/Cafeteria", 0);
            new BookHallTask(selectedDay, true, globalSettings.getBoolean("veggie", false)).execute();
            break;
        case 2:
            tracker.trackEvent("User actions", "Manual booking", "Formal", 0);
            new BookHallTask(selectedDay, false, globalSettings.getBoolean("veggie", false)).execute();
            break;
        case 3:
            tracker.trackEvent("User actions", "Manual cancelling", "", 0);
            new CancelHallTask(selectedDay).execute();
            break;
        }
        tracker.dispatch();
        return false;
    }

    /**
     * Toasts a message for the length {@link Toast.LENGTH_LONG} to the screen.
     *
     * @param message - The message to be toasted.
     */
    protected void localUIToast(String message) {
        Context context = getApplicationContext();
        CharSequence text = message;
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    /**
     * Updates the dates shown in the main list of the application.
     *
     * Will show the next 7 days based on the current date.
     */
    private void localUIUpdateDatesShown() {
        values = new ArrayList<String>();
        Calendar day = Calendar.getInstance();
        Date date = day.getTime();
        for (int i = 0; i < 5; i++) {
            values.add(formatPretty.format(date));
            day.add(Calendar.DAY_OF_MONTH, 1);
            date = day.getTime();
        }
        listAdapter = new MyListAdapter(DisplayHallInfoActivity.this, values,
                details);
        lv.setAdapter(listAdapter);
    }

    /**
     * Updates the status (First, Formal, No Hall etc.) of the dates shown in the application.
     *
     * The status of the bookings is determined by the local values in SharedPreferences.
     */
    private void localUIUpdateBookingStatus() {
        details = new ArrayList<String>();
        Calendar day = Calendar.getInstance();
        Date date = day.getTime();
        String hallType;
        for (int i = 0; i < 7; i++) {
            hallType = localGetHallBooking(globalSettings, date);
            if (day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY && hallType.equals("First Hall")){
                details.add("Cafeteria Hall");
            } else {
                details.add(hallType);
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
            date = day.getTime();
        }
        listAdapter = new MyListAdapter(DisplayHallInfoActivity.this, values,
                details);
        lv.setAdapter(listAdapter);
    }

    /**
     * A task (extending {@link android.os.AsyncTask}) to book hall. <b>Uses {@link netBookHall} to book</b>.
     *
     * An AsyncTask implementation is required as network activity is to be performed, and
     * must therefore be done on a thread other than the main UI thread.
     *
     * @see netBookHall
     *
     */
    protected class BookHallTask extends AsyncTask<String, Void, Boolean> {

        private Date day;
        private boolean firstHall;
        private boolean vegetarian;

        /**
         * Constructor for the task - pass in required values when instantiating a new task to be executed.
         *
         * @param day - the date to be booked
         * @param firstHall - true if first hall, false if formal (<b>Note: cafeteria hall is treated as first hall</b>)
         * @param veggie - true if vegetarian option is required
         */
        protected BookHallTask(Date day, boolean firstHall, boolean veggie) {
            this.day = day;
            this.firstHall = firstHall;
            this.vegetarian = veggie;
        }

        @Override
        protected Boolean doInBackground(String... params) {

            if (!netIsLoggedIn()){
                netLogin();
            }

            if (!netIsLoggedIn()){
                Log.e("Login error", "Should be logged in, something wrong (BookHallTask)");
                return false;
            }

            netBookHall(day, firstHall, vegetarian);

            try {
                return netPullOneBooking(day);
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
            globalDialog = ProgressDialog.show(DisplayHallInfoActivity.this,
                    "", "Booking...", true);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (!result) {
                localUIToast("Unable to book - something went wrong (server did not accept the booking)");
            }
            localUIUpdateBookingStatus();
            globalDialog.dismiss();
            DisplayHallInfoActivity.tasks.remove(this);
        }

    }

    /**
     * A task (extending {@link android.os.AsyncTask}) to cancel an existing hall booking. <b>Uses {@link netCancelHall} to cancel</b>.
     *
     * An AsyncTask implementation is required as network activity is to be performed, and
     * must therefore be done on a thread other than the main UI thread.
     *
     * @see netCancelHall
     *
     */
    private class CancelHallTask extends AsyncTask<String, Void, Boolean> {

        private Date day;

        /**
         * Constructor for the task - pass in required values when instantiating a new task to be executed.
         *
         * @param day - the day to be cancelled.
         */
        protected CancelHallTask(Date day) {
            this.day = day;
        }

        @Override
        protected Boolean doInBackground(String... params) {

            if (!netIsLoggedIn()){
                netLogin();
            }

            if (!netIsLoggedIn()){
                Log.e("Login error", "Should be logged in, something wrong (CancelHallTask)");
                return false;
            }

            netCancelHall(day);
            try {
                return !netPullOneBooking(day);
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
            globalDialog = ProgressDialog.show(DisplayHallInfoActivity.this,
                    "", "Cancelling...", true);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (!result) {
                localUIToast("Unable to cancel booking - something went wrong");
            }
            localUIUpdateBookingStatus();
            globalDialog.dismiss();
            DisplayHallInfoActivity.tasks.remove(this);
        }

    }

    /**
     * Books hall for a specific day.
     *
     * @param date - the day to be booked
     * @param firstHall - true if first hall, false if formal (<b>Note: cafeteria hall is treated as first hall</b>)
     * @param vegetarian - true if vegetarian option is required
     * @return boolean - true if booking was successful, false otherwise.
     */
    protected static boolean netBookHall(Date date, boolean firstHall,
            boolean vegetarian) {

        if (!(localGetHallBooking(globalSettings, date).equals("No Hall"))) {
            // Already made a booking, return false
            return false;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        String month = new String[] { "01", "02", "03", "04", "05", "06", "07",
                "08", "09", "10", "11", "12" }[calendar.get(Calendar.MONTH)];
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        String format = String.format("%%0%dd", 2);
        String sDay = String.format(format, day);
        int hallCode;
        String veggie = "0";
        if (!firstHall) {
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                hallCode = hallCodes[3];
            } else {
                hallCode = hallCodes[1];
            }
        } else {
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                hallCode = hallCodes[2];
            } else {
                hallCode = hallCodes[0];
            }
        }
        if (vegetarian)
            veggie = "1";
        String url = "https://www.cai.cam.ac.uk/mealbookings/index.php?event="
                + hallCode + "&date=" + year + "-" + month + "-" + sDay;
        
        // Get the special requirements set in preferences (Vegan etc.)
        String requirements = globalSettings.getString("specialRequirements", "");

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(5);
        nameValuePairs.add(new BasicNameValuePair("guests", "0"));
        nameValuePairs.add(new BasicNameValuePair("guests_names", ""));
        nameValuePairs.add(new BasicNameValuePair("vegetarians", veggie));
        nameValuePairs.add(new BasicNameValuePair("requirements", requirements));
        nameValuePairs.add(new BasicNameValuePair("update", "Create"));

        try {
            netPostData(url, nameValuePairs);
            Log.i("DisplayHallInfoActivity",
                    "Booked hall on " + date.toString());
            return true;
        } catch (ClientProtocolException e) {
            Log.e(TAG, "ClientProtocolException in netBookHall()");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }

    public static boolean netCancelHall(Date date) {

        String hallTypeCurrentlyBooked = localGetHallBooking(globalSettings,
                date);
        if (hallTypeCurrentlyBooked.equals("No Hall")) {
            // No booking to cancel
            return true;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        String month = new String[] { "01", "02", "03", "04", "05", "06", "07",
                "08", "09", "10", "11", "12" }[calendar.get(Calendar.MONTH)];
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        String format = String.format("%%0%dd", 2);
        String sDay = String.format(format, day);

        int hallCode;
        if (hallTypeCurrentlyBooked.equals("Formal Hall")) {
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                hallCode = hallCodes[3];
            } else {
                hallCode = hallCodes[1];
            }
        } else {
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                hallCode = hallCodes[2];
            } else {
                hallCode = hallCodes[0];
            }
        }

        String url = "https://www.cai.cam.ac.uk/mealbookings/index.php?event="
                + hallCode + "&date=" + year + "-" + month + "-" + sDay;

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        nameValuePairs.add(new BasicNameValuePair("delete_confirm", "Yes"));

        try {
            netPostData(url, nameValuePairs);
            return true;
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, "ClientProtocolException in netCancelHall()");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sets a booking locally on the device.
     *
     * @param settings - the SharedPreferences object for the application.
     * @param day - the day to book locally.
     * @param firstHall - true if first hall, false if formal (cafeteria hall is treated as first)
     * @param vegetarian - true if vegetarian meal is required
     * @return String[2] - an array of the day booked and the type of hall
     */
    protected static String[] localPutHallBooking(SharedPreferences settings,
            Date day, boolean firstHall, boolean vegetarian) {
        String veggie = vegetarian ? " - Vegetarian" : "";
        String hallType = firstHall ? "First Hall" + veggie : "Formal Hall"
                + veggie;
        String dayString = format.format(day);
        SharedPreferences.Editor editor = settings.edit();

        editor.putString(dayString, hallType);
        editor.commit();

        String[] result = new String[2];
        result[0] = dayString;
        result[1] = hallType;

        return result;
    }

    /**
     * Fetches the type of booking currently stored on the device for a specified day.
     *
     * @param settings - the SharedPreferences object for the application.
     * @param day - the day to retrieve
     * @return String - No Hall, First Hall, or Formal Hall
     */
    private static String localGetHallBooking(SharedPreferences settings,
            Date day) {
        return settings.getString(format.format(day), "No Hall");
    }

    /**
     * Cancels hall in the local device (removes it from the preferences).
     *
     * <b>This does not affect the server</b>.
     *
     * @param settings - SharedPreferences for the application
     * @param day - the day to cancel
     */
    protected static void localCancelHallBooking(SharedPreferences settings,
            Date day) {
        String dayS = format.format(day);
        SharedPreferences.Editor editor = settings.edit();
        editor.remove(dayS);
        editor.commit();
    }

    /**
     * Performs a HTTP GET on the given URL and returns the page's HTML as a string.
     *
     * @param url The page to be fetched
     * @return String The HTML of the page
     * @throws ClientProtocolException
     * @throws IOException
     */
    public static String netGetData(String url) throws ClientProtocolException,
            IOException {
        HttpGet get = new HttpGet(url);
        HttpResponse resp = httpClient.execute(get, httpContext);
        HttpEntity entity = resp.getEntity();
        return EntityUtils.toString(entity, HTTP.UTF_8);
    }

    public static String netPostData(String url,
            List<NameValuePair> nameValuePairs) throws ClientProtocolException,
            IOException {
        HttpPost post = new HttpPost(url);
        post.setEntity(new UrlEncodedFormEntity(nameValuePairs));

        HttpResponse resp = httpClient.execute(post, httpContext);
        HttpEntity entity = resp.getEntity();
        return EntityUtils.toString(entity, HTTP.UTF_8);
    }

    /**
     * Logs the user into the server using the stored username and password.
     *
     * @return String - The success or failure of the login.
     */
    //TODO: Change function to remove String return for better error checking.
    protected static String netLogin() {

        String crsid = globalSettings.getString("crsid", "");
        String password = globalSettings.getString("password", "");

        try {
            final String location = "https://raven.cam.ac.uk/auth/authenticate2.html";

            HttpPost post = new HttpPost(location);
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(5);
            nameValuePairs.add(new BasicNameValuePair("ver", "1"));
            nameValuePairs.add(new BasicNameValuePair("url", baseURL));
            nameValuePairs.add(new BasicNameValuePair("userid", crsid));
            nameValuePairs.add(new BasicNameValuePair("pwd", password));
            nameValuePairs.add(new BasicNameValuePair("submit", "Submit"));
            post.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            HttpResponse response = httpClient.execute(post, httpContext);
            HttpEntity entity2 = response.getEntity();
            mealBookingIndexHtml = EntityUtils.toString(entity2, HTTP.UTF_8);

            // In fact, this page is not meal booking
            // TODO: sort out page gets and posts
            Document doc = Jsoup.parse(mealBookingIndexHtml);
            Element error = doc.select("span.error").first();

            // TODO: make login error checking more robust
            if (error != null) {
                return "Something went wrong when logging in: " + error.text();
            } else {                
                return "Successfully logged in";
            }

        } catch (ClientProtocolException e) {
            e.printStackTrace();
            Log.e(TAG, "ClientProtocolException in netLogin()");
            return "ClientProtocol error";
        } catch (IOException e) {
            e.printStackTrace();
            return "IOException error (perhaps an SSL or URL problem)";
        }

    }

    /**
     * A task to pull all bookings from the server and sync them with the local device.
     *
     * If any differences are found, the server values will take priority (to prevent false information shown locally).
     *
     * @see netPullBookings
     */
    private class PullBookingsTask extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {

            if (!netIsLoggedIn()){
                netLogin();
            }

            if (!netIsLoggedIn()){
                Log.e("Login error", "Should be logged in, something wrong (PullBookingsTask)");
                return false;
            }

            try {
                return netPullBookings();
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
            globalDialog = ProgressDialog.show(DisplayHallInfoActivity.this,
                    "", "Fetching bookings from server", true);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (!result) {
                localUIToast("Something went wrong when pulling multiple bookings from the server");
            }
            localUIUpdateDatesShown();
            localUIUpdateBookingStatus();
            DisplayHallInfoActivity.tasks.remove(this);
            globalDialog.dismiss();
        }

    }

    /**
     * Fetches the supplied date and syncs it with the local device.
     *
     * Similar to {@link netPullBookings}, but less intensive when only one booking is required (such as for verification).
     *
     * @param date - the date to pull
     * @return boolean - true if booking was found for the date given, false otherwise
     * @throws IOException
     * @throws ParseException
     */
    protected static boolean netPullOneBooking(Date date) throws IOException, ParseException {
        // Parse the date into a readable string, the same as the one on the
        // server
        String dateString = formatPretty.format(date);

        // Connect to the server, find the required link
        String docHtml = netGetData(baseURL);
        Document doc = Jsoup.parse(docHtml);
        String linkSelector = "table.list tr:contains(" + dateString
                + ") a:contains(View/Edit) ";
        Element link = doc.select(linkSelector).first();

        if (link == null) {
            // Date is not currently booked on the server, cancel local booking
            // and return false
            localCancelHallBooking(globalSettings, date);
            Log.w(TAG, "No hall booking found on server for " + dateString);
            return false;
        } else {
            // Open the associated page with details of the booking
            String pageHtml = netGetData(baseURL + link.attr("href"));
            Document page = Jsoup.parse(pageHtml);

            // Gather details of the booking
            String dateBooking = page
                    .select("table.list td:contains(Date) ~ td").first().text();
            Date theDate = formatPretty.parse(dateBooking);
            String hallType = page.select("h1").first().text();
            Boolean firstHall = hallType.indexOf("First") != -1 || hallType.indexOf("Cafeteria") != -1;
            Boolean veggie = Integer.parseInt(page
                    .select("table.list td:contains(Vegetarians) ~ td").first()
                    .text()) > 0;

            // Add it to the local settings
            localPutHallBooking(globalSettings, theDate, firstHall, veggie);

            Log.i(TAG, "Booking on server for " + dateString + " was "
                    + hallType + " + veggie=" + veggie);
            return true;
        }
    }

    /**
     * Function to pull all bookings from the server and sync to the local device.
     *
     * @return boolean - true on success
     * @throws IOException
     * @throws ParseException
     * @see PullBookingsTask
     */
    private boolean netPullBookings() throws IOException,
            ParseException {

        Log.d(TAG, "netPullBookings();");

        String bookingsHtml = netGetData(baseURL);

        Document doc = Jsoup.parse(bookingsHtml);

        if (doc == null) {
            return false;
        } else {
            String linkSelector = "table.list a:contains(View/Edit)";
            Elements links = doc.select(linkSelector);

            ArrayList<String> bookingDates = new ArrayList<String>();

            String linkHtml;
            Document page;
            for (Element link : links) {
                linkHtml = netGetData(baseURL + link.attr("href"));
                page = Jsoup.parse(linkHtml);
                if (page == null) {
                    return false;
                } else {
                    String date = page
                            .select("table.list td:contains(Date) ~ td")
                            .first().text();
                    Date theDate = formatPretty.parse(date);
                    String hallType = page.select("h1").first().text();
                    Boolean firstHall = hallType.indexOf("First") != -1 || hallType.indexOf("Cafeteria") != -1;
                    Boolean veggie = Integer.parseInt(page
                            .select("table.list td:contains(Vegetarians) ~ td")
                            .first().text()) > 0;
                    bookingDates.add(localPutHallBooking(globalSettings,
                            theDate, firstHall, veggie)[0]);
                }

            }

            // clear all local bookings that were not on the server
            Set<String> allSettingKeys = globalSettings.getAll().keySet();

            for (String key : allSettingKeys) {
                if (key.indexOf("20") != -1 && !(bookingDates.contains(key))) {
                    globalSettingsEditor.remove(key);
                }
            }
            globalSettingsEditor.commit();

            return true;
        }

    }

    /**
     * Finds the next available date of the given day.
     *
     * @param requiredDay - the day to be found
     * @return Date - the specific date of the next occurence of {@link requiredDay}
     */
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_preferences:
            Intent intent = new Intent(this, PrefsActivity.class);
            startActivity(intent);
            break;
        case R.id.menu_refresh:
            new PullBookingsTask().execute();
            break;
        case R.id.menu_print_settings:
            Log.i("GLOBALSETTINGS", globalSettings.getAll().toString());
            Log.i("DEV", httpContext.toString());
            Log.i("DEV", httpClient.getParams().toString());
            Log.i("DEV", cookieStore.getCookies().toString());
            localUIToast("settings: " + globalSettings.getAll().size());
            localUIToast("revision: "
                    + globalSettings.getString("app_revision", "unknown"));
            break;
        }
        return false;
    }
}
