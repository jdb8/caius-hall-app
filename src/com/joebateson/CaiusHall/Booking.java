package com.joebateson.CaiusHall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import android.database.Cursor;
import android.util.Log;

public class Booking {
    private final int id;
    private final String dateString;
    private final int dayOfWeek;
    private final int hallType;
    private int placesRemaining;
    private String bookingInfo;
    private boolean isBooked;

    private int vegetarianCount;
    private String additionalRequirements;
    private int guestCount;

    private String bookingURL;

    public Booking(Cursor cursor) throws CaiusHallException {
        if (cursor.getCount() != 1) {
            throw new CaiusHallException("Expected a cursor with one booking");
        }

        cursor.moveToFirst();

        this.id = cursor.getInt(cursor.getColumnIndex(BookingDBHelper.KEY_ID));
        this.dateString = cursor.getString(cursor
                .getColumnIndex(BookingDBHelper.KEY_DATE));
        this.dayOfWeek = cursor.getInt(cursor.getColumnIndex(BookingDBHelper.KEY_DAY_OF_WEEK));
        this.hallType = cursor.getInt(cursor.getColumnIndex(BookingDBHelper.KEY_HALL_TYPE));
        this.placesRemaining = cursor.getInt(cursor.getColumnIndex(BookingDBHelper.KEY_PLACES_REMAINING));
        this.bookingInfo = cursor.getString(cursor.getColumnIndex(BookingDBHelper.KEY_BOOKING_INFO));
        this.isBooked = (cursor.getInt(cursor.getColumnIndex(BookingDBHelper.KEY_IS_BOOKED)) != 0);

        this.vegetarianCount = cursor.getInt(cursor.getColumnIndex(BookingDBHelper.KEY_NUMBER_OF_VEGETARIANS));
        this.additionalRequirements = cursor.getString(cursor.getColumnIndex(BookingDBHelper.KEY_ADDITIONAL_REQUIREMENTS));
        this.guestCount = cursor.getInt(cursor.getColumnIndex(BookingDBHelper.KEY_GUESTS));

        cursor.close();

        bookingURL = DisplayHallInfoActivity.buildBookingURL(dateString, dayOfWeek, hallType);
    }

    /**
     * Books on the server. Does not change local variables (this should be done
     * during the verification process).
     *
     * @param vegetarians - an integer representing the number of vegetarians
     * @param requirements - a string for additional requirements
     * @param guests - an integer representing the number of guests
     * @throws CaiusHallException
     * @throws AlreadyBookedException - thrown if the user is already booked in
     */
    public void book(int vegetarians, String requirements, int guests) throws CaiusHallException, AlreadyBookedException {
        if (!isBooked()){
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(5);
            nameValuePairs.add(new BasicNameValuePair("guests", guests + ""));
            nameValuePairs.add(new BasicNameValuePair("guests_names", ""));
            nameValuePairs.add(new BasicNameValuePair("vegetarians", vegetarians + ""));
            nameValuePairs.add(new BasicNameValuePair("requirements", requirements));
            nameValuePairs.add(new BasicNameValuePair("update", "Create"));

            try {
                DisplayHallInfoActivity.netPostData(bookingURL, nameValuePairs);
                Log.i("Booking " + id, "Booked hall on " + dateString);
                isBooked = true;
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                throw new CaiusHallException("ClientProtocolException in Booking.book() with id = " + id);
            } catch (IOException e) {
                e.printStackTrace();
                throw new CaiusHallException("IOException in Booking.book() with id = " + id);
            }
        } else {
            throw new AlreadyBookedException("Booking " + id + " already marked as booked in local database");
        }
    }

    /**
     * Compares the booking information stored in this object with the data
     * on the server. Updates the local data to match the server data regardless of
     * success or failure.
     *
     * @return true if no booking data was found different, false otherwise
     *      (data unrelated to the user's booking is ignored in this check)
     * @throws CaiusHallException
     */
    public boolean verifyAndSyncWithServer() throws CaiusHallException{

        boolean serverIsBooked = false,
                localIsBooked = isBooked();
        int serverGuests = -1, localGuests = this.guestCount,
            serverVeggies = -1, localVeggies = this.vegetarianCount,
            serverPlacesRemaining = -1;
        String serverRequirements = "", localRequirements = this.additionalRequirements,
               serverBookingInfo = "";

        try {
            String docHtml = DisplayHallInfoActivity.netGetData(bookingURL);
            Document doc = Jsoup.parse(docHtml);
            Elements userBookingDetails = doc.select("table.list:contains(dietary)"),
                     generalBookingDetails = doc.select("table.list:contains(date)"),
                     bookingInfo = doc.select("p"), // Seems to work!
                     guests = userBookingDetails.select("td:contains(Guests) ~ td"),
                     veggies = userBookingDetails.select("td:contains(Vegetarians) ~ td"),
                     requirements = userBookingDetails.select("td:contains(dietary) ~ td"),
                     placesRemaining = generalBookingDetails.select("td:contains(places) ~ td");


            if (!guests.isEmpty() && !veggies.isEmpty()){
                // The server has a booking registered
                serverIsBooked = true;
                serverGuests = Integer.parseInt(guests.first().val());
                serverVeggies = Integer.parseInt(veggies.first().val());
                serverRequirements = requirements.first().val();
            }

            // Convert from the form xx/yy to {xx, yy}[0], then parse xx as integer
            serverPlacesRemaining = Integer.parseInt(placesRemaining.first().val().split("\\")[0]);
            serverBookingInfo = bookingInfo.first().val();

        } catch (ClientProtocolException e) {
            e.printStackTrace();
            throw new CaiusHallException("ClientProtocolException in Booking.verifyAndSyncWithServer() with id = " + id);
        } catch (IOException e) {
            e.printStackTrace();
            throw new CaiusHallException("IOException in Booking.verifyAndSyncWithServer() with id = " + id);
        }

        // Update all values to the server specified values
        this.isBooked = serverIsBooked;
        this.guestCount = serverGuests;
        this.vegetarianCount = serverVeggies;
        this.additionalRequirements = serverRequirements;
        this.placesRemaining = serverPlacesRemaining;
        this.bookingInfo = serverBookingInfo;

        // Only include user specified data in the equality check
        return (serverIsBooked == localIsBooked) &&
               (serverGuests == localGuests) &&
               (serverVeggies == localVeggies) &&
               (serverRequirements.equals(localRequirements));
    }

    public boolean isBooked(){
        return isBooked;
    }


}
