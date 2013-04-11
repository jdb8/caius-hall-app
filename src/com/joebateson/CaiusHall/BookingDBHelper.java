package com.joebateson.CaiusHall;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class BookingDBHelper extends SQLiteOpenHelper {

    private static final String TAG = "CaiusHall - BookingDBHelper";

    private static final String DATABASE_NAME = "caiushallbookings.db";
    private static final int DATABASE_VERSION = 5;

    // Table information
    public final static String TABLE_NAME="Bookings";
    public final static String KEY_ID="_id";
    public final static String KEY_DATE="date";
    public final static String KEY_DAY_OF_WEEK="day_of_week"; // 1 = sunday, 2 = monday... 7 = saturday (to match Calendar.DAY_OF_WEEK)
    public final static String KEY_HALL_TYPE="hall_type";
    public final static String KEY_PLACES_REMAINING="places_remaining";
    public final static String KEY_MENU="menu";
    public final static String KEY_BOOKING_INFO="booking_info"; // Any additional information about the booking
    public final static String KEY_IS_BOOKED="is_booked";
    // Following only applicable for bookings the user is booked into
    public final static String KEY_NUMBER_OF_VEGETARIANS="number_vegetarian";
    public final static String KEY_ADDITIONAL_REQUIREMENTS="additional_requirements";
    public final static String KEY_GUESTS="number_of_guests";

    private static final String DATABASE_CREATE = "create table " + TABLE_NAME + " ( " +
            KEY_ID + " integer primary key autoincrement, " +
            KEY_DATE + " text not null, " +
            KEY_DAY_OF_WEEK + " integer not null, " +
            KEY_HALL_TYPE + " integer not null, " +
            KEY_PLACES_REMAINING + " integer not null, " +
            KEY_MENU + " text not null, " +
            KEY_BOOKING_INFO + " text not null, " +
            KEY_IS_BOOKED + " integer not null, " +
            KEY_NUMBER_OF_VEGETARIANS + " integer not null, " +
            KEY_ADDITIONAL_REQUIREMENTS + " text not null, " +
            KEY_GUESTS + " integer not null, " +
            "unique (" + KEY_DATE + ", " + KEY_HALL_TYPE + ")" +
            ");";

    BookingDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating db " + DATABASE_NAME);
        db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading db from version " + oldVersion + " to " + newVersion);
        Log.i(TAG, "Deleting " + TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}
