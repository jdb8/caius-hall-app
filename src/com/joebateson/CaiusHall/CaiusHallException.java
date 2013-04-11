package com.joebateson.CaiusHall;

/**
 * General exception to give more information when things go wrong.
 */
public class CaiusHallException extends Exception {

    public CaiusHallException() {}

    public CaiusHallException(String message){
        super(message);
    }
}
