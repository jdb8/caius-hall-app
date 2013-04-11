package com.joebateson.CaiusHall;

/**
 * Exception to throw if the booking is already marked as booked in the local database.
 */
public class AlreadyBookedException extends Exception {

    public AlreadyBookedException() {}

    public AlreadyBookedException(String message){
        super(message);
    }
}
