package com.example.android.repeatingalarm;

/**
 * Created by Dein Name on 01.11.2016.
 */


import java.util.*;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import com.example.android.common.logger.Log;

import java.util.GregorianCalendar;
import android.app.Service;

public class Clock {
    public static final int TICKPERSECOND = 0;
    public static final String TAG = "Clock";

    private GregorianCalendar Calendar;
    private TimeZone TimeZone;
    private Handler Handler;
    private List<OnClockTickListner> OnClockTickListenerList = new ArrayList<OnClockTickListner>();

    private Runnable Ticker;

    private BroadcastReceiver IntentReceiver;
    private IntentFilter IntentFilter;

    private int TickMethod = 0;
    Context Context;
    private Boolean hold = false;
    private static final int m_tickInMillis = 4000;
    private Boolean bTimeIsTrustworthy = true;
    private long lastTimevalue;

    private long timeinMilliseconds() {
        return ((this.Calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                this.Calendar.get(Calendar.MINUTE) * 60 +
                this.Calendar.get(Calendar.SECOND)) * 1000 +
                this.Calendar.get(Calendar.MILLISECOND));

    }

    public Boolean isTimeTrustworthy() {
        return bTimeIsTrustworthy;
    }

    public void setTimeTrustworthy() {
        bTimeIsTrustworthy=true;
    }

    public Clock(Context context, int tickMethod, GregorianCalendar calendar) {
        this.Context = context;
        this.TickMethod = tickMethod;
        this.Calendar = calendar;
        Calendar now= Calendar.getInstance();
        lastTimevalue = now.getTimeInMillis();

        switch (TickMethod) {
            case 0:
                this.StartTickPerSecond();
                break;
            default:
                break;
        }
    }

    public void decreaseClock() {
        long time = timeinMilliseconds();

        if (time > m_tickInMillis)
            this.Calendar.add(Calendar.MILLISECOND, -m_tickInMillis);
        else {
            this.Calendar.set(Calendar.HOUR_OF_DAY, 0);
            this.Calendar.set(Calendar.MINUTE, 0);
            this.Calendar.set(Calendar.SECOND, 0);
            this.Calendar.set(Calendar.MILLISECOND, 0);

        }
    }

    private void Tick(int tickInMillis) {
        this.NotifyOnTickListners();
    }

    private void NotifyOnTickListners() {
        switch (TickMethod) {
            case 0:
                for (OnClockTickListner listner : OnClockTickListenerList) {
                    listner.OnSecondTick(Calendar);
                }
                break;
        }
    }

    private void StartTickPerSecond() {
        this.Handler = new Handler();
        this.Ticker = new Runnable() {
            public void run() {
                Calendar today= Calendar.getInstance();
                if (!hold) {
                    // don't check while screen is of -> energy save mode stops actualisation
                    // check plausibility of time
                    if (lastTimevalue>today.getTimeInMillis() ||
                            lastTimevalue + 30*60*1000 < today.getTimeInMillis()) {
                        bTimeIsTrustworthy=false;
                        MainActivity.appendLog("time not thrustworthy any longer last diff in minutes = " +(today.getTimeInMillis()-lastTimevalue)/1000/60);
                    }
                    Tick(m_tickInMillis);
                }
                lastTimevalue = today.getTimeInMillis();

                long now = SystemClock.uptimeMillis();
                long next = now + (m_tickInMillis - now % m_tickInMillis);
                Handler.postAtTime(Ticker, next);
            }
        };
        this.Ticker.run();

    }

    public void holdClock() {
        MainActivity.appendLog("Clock Hold Clock");
        hold = true;
    }

    public void resumeClock() {
        Calendar today= Calendar.getInstance();
        lastTimevalue = today.getTimeInMillis();
        hold = false;
    }

    public void StopTick() {
        MainActivity.appendLog("Clock Stop Tick");
        if (this.IntentReceiver != null) {
            this.Context.unregisterReceiver(this.IntentReceiver);
        }
        if (this.Handler != null) {
            this.Handler.removeCallbacks(this.Ticker);
        }
    }

    public GregorianCalendar GetCurrentTime() {
        return this.Calendar;
    }

    public void AddClockTickListner(OnClockTickListner listner) {
        this.OnClockTickListenerList.add(listner);
    }
}

