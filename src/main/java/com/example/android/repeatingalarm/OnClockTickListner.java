package com.example.android.repeatingalarm;

/**
 * Created by Dein Name on 01.11.2016.
 */
import java.util.GregorianCalendar;



public interface OnClockTickListner {
    public void OnSecondTick(GregorianCalendar currentTime);
}