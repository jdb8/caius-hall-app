package com.joebateson.CaiusHall;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
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

    public static final String TAG = "CaiusHall";

    private ListView lv;
    private MyListAdapter listAdapter;
    private ArrayList<String> values;
    private ArrayList<String> details;

    private Date selectedDay = null;
    private String baseURL = "https://www.cai.cam.ac.uk/mealbookings/";

    // For debugging purposes
    //private String baseURL = "http://192.168.0.9:8888";

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

    // Useful method from: http://www.tristanwaddington.com/2011/07/update-hg-or-git-changest-during-android-build/
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.hall_list);

        lv = (ListView) findViewById(R.id.lvResult);
        registerForContextMenu(lv);

        globalSettings = PreferenceManager.getDefaultSharedPreferences(this);
        globalSettingsEditor = globalSettings.edit();

        globalSettingsEditor.putString("app_revision", this.getAppChangsetFromPropertiesFile());
        globalSettingsEditor.commit();

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
        }
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
            day.add(Calendar.DAY_OF_MONTH, 1);
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
            day.add(Calendar.DAY_OF_MONTH, 1);
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
            globalDialog = ProgressDialog.show(DisplayHallInfoActivity.this, "",
                    "Booking...", true);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (!result){
                localUIToast("Unable to book - something went wrong (server did not accept the booking)");
            }
            localUIUpdateBookingStatus();
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
        int hallCode;
        String veggie = "0";
        if (!firstHall) {
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY){
                hallCode = 169;
            } else {
                hallCode = 168;
            }
        } else {
            hallCode = 167;
        }
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

    private String netLogin(String crsid, String password) {

        try {
            // Use the custom HttpClient class to trust the raven certificate
            httpClient = new AdditionalCertHttpClient(getApplicationContext());
            httpContext = new BasicHttpContext();
            cookieStore = new BasicCookieStore();
            httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);

            // This URL may change, works currently however
            //String location = "https://raven.cam.ac.uk/auth/authenticate.html?ver=1&url=https%3a%2f%2fwww.cai.cam.ac.uk%2fmealbookings%2f";
            String location = "https://raven.cam.ac.uk/auth/authenticate2.html";

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
                loggedIn = true;
                return "Successfully logged in";
            }

        } catch (ClientProtocolException e) {
            e.printStackTrace();
            return "ClientProtocol error";
        } catch (IOException e) {
            e.printStackTrace();
            return "IOException error (perhaps an SSL or URL problem)";
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
                localUIToast("Something went wrong when pulling multiple bookings from the server");
            }
            localUIUpdateDatesShown();
            localUIUpdateBookingStatus();
            DisplayHallInfoActivity.tasks.remove(this);
            globalDialog.dismiss();
        }



    }

    private boolean netPullOneBooking(Date date) throws IOException, ParseException{
        // Parse the date into a readable string, the same as the one on the server
        String dateString = formatPretty.format(date);

        // Connect to the server, find the required link
        String docHtml = netGetData(baseURL);
        Document doc = Jsoup.parse(docHtml);
        String linkSelector = "table.list tr:contains(" + dateString + ") a:contains(View/Edit) ";
        Element link = doc.select(linkSelector).first();

        if (link == null){
            // Date is not currently booked on the server, cancel local booking and return false
            localCancelHallBooking(globalSettings, date);
            Log.w(TAG, "No hall booking found on server for " + dateString);
            return false;
        } else {
            // Open the associated page with details of the booking
            String pageHtml = netGetData(baseURL + link.attr("href"));
            Document page = Jsoup.parse(pageHtml);

            // Gather details of the booking
            String dateBooking = page.select("table.list td:contains(Date) ~ td").first().text();
            Date theDate = formatPretty.parse(dateBooking);
            String hallType = page.select("h1").first().text();
            Boolean firstHall = hallType.indexOf("First") != -1;
            Boolean veggie = Integer.parseInt(page.select("table.list td:contains(Vegetarians) ~ td").first().text()) > 0;

            // Add it to the local settings
            localPutHallBooking(globalSettings, theDate, firstHall, veggie);

            Log.i(TAG, "Booking on server for " + dateString + " was " + hallType + " + veggie=" + veggie);
            return true;
        }
    }

    private boolean netPullBookings(String url) throws IOException, ParseException{

        if (!loggedIn){
            return false;
        }

        String bookingsHtml = netGetData(url);

        Document doc = Jsoup.parse(bookingsHtml);

        if (doc == null) {
            return false;
        } else {
            String linkSelector = "table.list a:contains(View/Edit)";
            Elements links = doc.select(linkSelector);

            ArrayList<String> bookingDates = new ArrayList<String>();

            String linkHtml;
            Document page;
            for (Element link : links){
                linkHtml = netGetData(url + link.attr("href"));
                page = Jsoup.parse(linkHtml);
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
                break;
        case R.id.menu_print_settings:
                Log.i("GLOBALSETTINGS", globalSettings.getAll().toString());
                Log.i("DEV", httpContext.toString());
                Log.i("DEV", httpClient.getParams().toString());
                Log.i("DEV", cookieStore.getCookies().toString());
                localUIToast("revision: " + globalSettings.getString("app_revision", "unknown"));
                break;
        }
        return false;
    }
}
