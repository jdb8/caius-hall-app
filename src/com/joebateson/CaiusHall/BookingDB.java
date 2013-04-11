package com.joebateson.CaiusHall;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class BookingDB {

    private static final String TAG = "CaiusHall - BookingDB";

    private BookingDBHelper dbHelper;
    private SQLiteDatabase db;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static final int HALL_TYPE_FIRST = 1;
    public static final int HALL_TYPE_FORMAL = 2;
    public static final int HALL_TYPE_SPECIAL = 3;

    public BookingDB(Context context){
        dbHelper = new BookingDBHelper(context);
    }

    /**
     * Adds a booking to the database.
     *
     * @param date
     *            a date object representing the date for the booking
     * @param hallType
     *            an integer corresponding to the values HALL_TYPE_FIRST,
     *            HALL_TYPE_FORMAL, HALL_TYPE_SPECIAL
     * @param placesRemaining
     *            an integer representing the number of places remaining
     * @param menu
     *            a string representation of the menu
     * @param bookingInfo
     *            additional info about the booking
     * @param isBooked
     *            true if user has booked this booking, false otherwise
     * @param numberOfVegetarians
     *            a count of the vegetarians attending
     * @param additionalRequirements
     *            dietary or allergy requirements
     * @param numberOfGuests
     *            a count of the guests attending
     * @return the row ID of the inserted booking, or -1 if the insertion
     *         failed.
     */
    public long addBooking(Date date, int hallType, int placesRemaining,
            String menu, String bookingInfo, boolean isBooked,
            int numberOfVegetarians, String additionalRequirements,
            int numberOfGuests) {

        db = dbHelper.getWritableDatabase();
        long rowId = -1;

        try{
            bookingInfo = (bookingInfo != null) ? bookingInfo : "";

            additionalRequirements = (additionalRequirements != null) ? additionalRequirements : "";

            ContentValues booking = new ContentValues();

            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            booking.put(BookingDBHelper.KEY_DATE, DATE_FORMAT.format(date));
            booking.put(BookingDBHelper.KEY_DAY_OF_WEEK, cal.get(Calendar.DAY_OF_WEEK));
            booking.put(BookingDBHelper.KEY_HALL_TYPE, hallType);
            booking.put(BookingDBHelper.KEY_PLACES_REMAINING, placesRemaining);
            booking.put(BookingDBHelper.KEY_MENU, menu);
            booking.put(BookingDBHelper.KEY_BOOKING_INFO, bookingInfo);
            booking.put(BookingDBHelper.KEY_IS_BOOKED, isBooked);

            if (!isBooked){
                numberOfVegetarians = 0;
                additionalRequirements = "";
                numberOfGuests = 0;
            }

            booking.put(BookingDBHelper.KEY_NUMBER_OF_VEGETARIANS, numberOfVegetarians);
            booking.put(BookingDBHelper.KEY_ADDITIONAL_REQUIREMENTS, additionalRequirements);
            booking.put(BookingDBHelper.KEY_GUESTS, numberOfGuests);

            rowId = db.insertOrThrow(BookingDBHelper.TABLE_NAME, null, booking);
        } finally {
            db.close();
        }

        return rowId;
    }

    /**
     * Selects a booking from the database by ID.
     *
     * The ID is the primary key of the table and thus this will always return one booking.
     * @param id the id of the booking required
     * @return a Booking object representing the booking
     * @throws CaiusHallException
     */
    public Booking getBookingById(int id) throws CaiusHallException{
        db = dbHelper.getReadableDatabase();

        String[] selectionArgs = {id + ""};
        String queryText = BookingDBHelper.KEY_ID + " = ?";
        Cursor result = db.query(BookingDBHelper.TABLE_NAME, null, queryText, selectionArgs, null, null, null);

        if (result.getCount() != 1){
            throw new CaiusHallException("Multiple bookings founds in database for id " + id);
        }

        return new Booking(result);
    }

    /**
     * Selects a booking from the database by (date, hallType).
     *
     * (date, hallType) are unique and thus this will always return one booking.
     * @param date the date required as a date object
     * @param hallType an integer corresponding to the values HALL_TYPE_FIRST, HALL_TYPE_FORMAL, HALL_TYPE_SPECIAL
     * @return a Cursor with the row containing the booking
     * @throws CaiusHallException
     */
    public Booking getBooking(Date date, int hallType) throws CaiusHallException {
        db = dbHelper.getReadableDatabase();

        String[] selectionArgs = {DATE_FORMAT.format(date), hallType + ""};
        String queryText = BookingDBHelper.KEY_DATE + " = ? AND " + BookingDBHelper.KEY_HALL_TYPE + " = ?";
        Cursor result =  db.query(BookingDBHelper.TABLE_NAME, null, queryText, selectionArgs, null, null, null);

        if (result.getCount() != 1){
            throw new CaiusHallException("Multiple bookings founds in database for date " + DATE_FORMAT.format(date) + " and hallType " + hallType);
        }

        return new Booking(result);
    }

    public int[] getAllBookings(){
        db = dbHelper.getReadableDatabase();
        String[] columns = {BookingDBHelper.KEY_ID};
        Cursor result = db.query(BookingDBHelper.TABLE_NAME, columns, null, null, null, null, null);

        int count = result.getCount();
        int[] ids = new int[count];
        result.moveToFirst();
        for (int i = 0; i < count; i++) {
            ids[i] = result.getInt(0);
            result.moveToNext();
        }

        result.close();

        return ids;
    }

    public void empty(){
        db = dbHelper.getWritableDatabase();
        try {
            int deleted = db.delete(BookingDBHelper.TABLE_NAME, "1", null);
            Log.d(TAG, "Deleted " + deleted + " rows from " + BookingDBHelper.TABLE_NAME);
        } finally {
            db.close();
        }
    }

    public void close(){
        db.close();
        db.close();
    }

}
