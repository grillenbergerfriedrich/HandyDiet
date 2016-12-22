package com.example.android.repeatingalarm;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.SyncStateContract;
import android.support.v4.app.ActivityCompat;
import android.text.format.DateFormat;

import java.net.InetAddress;
import java.util.Calendar;

import com.example.android.common.logger.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;


/**
 * Created by Dein Name on 05.11.2016.
 */

public class TimeCalculationService extends Service {
    private static Clock clock=null;
    private GregorianCalendar Calendar;
    public static final String TAG = "TimeCalculation";
    private int remainingTime;
    private int timeAlarmLastDisplayed=10;
    String filename = "RepeatingAlarmData";
    private int daylastused;
    private Boolean mainActivityclosed=false;
    private int timerCounter =0;
    private static boolean isRunning = false;


    @Override
    public IBinder onBind(Intent intent) {
        // A client is binding to the service with bindService()
        return null;
        }



    private final BroadcastReceiver m_DateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(Intent.ACTION_TIME_CHANGED))
            {
                MainActivity.appendLog("TimerCalculationService Date was changed");
               // time_chaned also happens at day switch ? bDateChanged=true;
            }
        }
    };

    //Create broadcast object
    private final BroadcastReceiver mybroadcast = new BroadcastReceiver() {
        //When Event is published, onReceive method is called
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (clock!=null)
                    clock.resumeClock();
            }
            else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (clock!=null)
                    clock.holdClock();
            } else if (intent.getAction().equals("PasswordRemainingTime")) {
                passwordRemainingTime();
            }

        }
    };

    @Override
    public void onCreate() {
        MainActivity.appendLog("TimeCalcService was created");
        isRunning = true;
    }

    public static boolean isRunning()
    {
        return isRunning;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        initTimeCalculation(intent);
        registerReceiver(mybroadcast, new IntentFilter(Intent.ACTION_SCREEN_ON));
        registerReceiver(mybroadcast, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        registerReceiver(mybroadcast, new IntentFilter("PasswordRemainingTime"));

        IntentFilter s_intentFilter= new IntentFilter();
        s_intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(m_DateChangedReceiver, s_intentFilter);

        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        Notification noti = new Notification.Builder(getApplicationContext())
                .setContentTitle("handy")
                .setContentText("diet")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1234, noti);

        return Service.START_STICKY;
    }

    public void passwordRemainingTime() {
        this.Calendar.set( Calendar.AM_PM, Calendar.AM );
        this.Calendar.set(Calendar.MILLISECOND, 0);
        this.Calendar.set(Calendar.SECOND, 0);
        this.Calendar.set(Calendar.MINUTE, 0);
        this.Calendar.set(Calendar.HOUR_OF_DAY, 1);
    }

    public void initRemainingTime() {
        this.Calendar.set( Calendar.AM_PM, Calendar.AM );
        this.Calendar.set(Calendar.MILLISECOND, 0);
        this.Calendar.set(Calendar.SECOND, 0);
        this.Calendar.set(Calendar.MINUTE, 45);
        this.Calendar.set(Calendar.HOUR_OF_DAY, 1);
    }

    private void initClock() {
        this.Calendar = new java.util.GregorianCalendar();
        this.Calendar.setTime(new Date());
        initRemainingTime();
        ObjectInputStream objinputStream;

        try {
            objinputStream = new ObjectInputStream(getApplicationContext().openFileInput(filename));
            daylastused=(int)objinputStream.readObject();
            GregorianCalendar today = new GregorianCalendar();
            today.setTime(new Date());
            if (daylastused==(int)today.get(Calendar.DAY_OF_MONTH))
                this.Calendar.setTime((Date)objinputStream.readObject());
            objinputStream.close();
            MainActivity.appendLog("TimerCalculationService Data loaded");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int timeInSeconds(GregorianCalendar currentTime){
        return (currentTime.get(Calendar.HOUR_OF_DAY)*3600+
                currentTime.get(Calendar.MINUTE)*60+
                currentTime.get(Calendar.SECOND));

    }

    public void isServiceRunning(Context context) {

        Log.i(TAG, "Checking if service is running");

        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);

        List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        boolean isServiceFound = false;

        for (int i = 0; i < services.size(); i++) {
            Log.i(TAG, services.get(i).service.getClassName()+ " running");
        }

    }

    private boolean screenIsLocked() {
        KeyguardManager km = (KeyguardManager) getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
        if (km==null) {
            MainActivity.appendLog("keyguardmanager is null");
            return true;
        }
        boolean locked = km.inKeyguardRestrictedInputMode();
        // do not count while screen is locked
        if (locked) {
            if (MainActivity.bDebug)
                Log.d(TAG,"screen is locked");
        }
        return locked;
    }

    public void initTimeCalculation(Intent intent) {
        initClock();

        clock=new Clock(getApplicationContext(),Clock.TICKPERSECOND,this.Calendar);
        clock.AddClockTickListner(new OnClockTickListner() {

            @Override
            public void OnSecondTick(GregorianCalendar currentTime) {
                // is screen is locked -> do nothing
                if (screenIsLocked())
                    return;

                // check ehther day has changed
                if (checkDayChanged())
                    saveData();

                MainActivity.appendLog(clock.isTimeTrustworthy() + " " + daylastused + " remaining time " +  currentTime.get(Calendar.HOUR_OF_DAY)+":" + DateFormat.format("mm:ss", currentTime.getTime()).toString());
                Log.d(TAG, "day "+ daylastused + " remaining time "  + currentTime.get(Calendar.HOUR_OF_DAY)+":" + DateFormat.format("mm:ss", currentTime.getTime()).toString());
                timerCounter++;
                if (timerCounter%20==0)
                    saveData();
                if (!mainActivityclosed) {
                    timerCounter++;
                    if (timerCounter > 10) {
                        sendBroadcast(new Intent("StoppActivity"));
                        mainActivityclosed = true;
                    }
                    return;
                }
                remainingTime=timeInSeconds(currentTime);
                if (timeAlarmLastDisplayed==10 && remainingTime>2)
                    timeAlarmLastDisplayed=remainingTime;
                if (timeAlarmLastDisplayed*0.5>remainingTime){
                    MainActivity.appendLog("activate Screen ");
                    Intent inent = new Intent(getApplicationContext(), MainActivity.class);
                    inent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(inent);
                    if (remainingTime>2)
                        timeAlarmLastDisplayed=remainingTime;
                }
                // after all decrease clock !!
                clock.decreaseClock();
            }
        });

    }

    public static Date getCurrentNetworkTime() {
        SntpClient client = new SntpClient();
        // timeout 5 seconds
        if (client.requestTime("time.foo.com",50000)) {
            long now = client.getNtpTime() + SystemClock.elapsedRealtime() -
                    client.getNtpTimeReference();
            Date current = new Date(now);
            Log.i("NTP tag", current.toString());
            return current;
        }
        return null;
    }

    private Boolean checkDayChanged() {
        if (!clock.isTimeTrustworthy())
            return false;

        Calendar today= Calendar.getInstance();
        if (daylastused!=(int)today.get(Calendar.DAY_OF_MONTH)) {
            daylastused=(int)today.get(Calendar.DAY_OF_MONTH);
            MainActivity.appendLog("new day -> new remaining time");
            initRemainingTime();
            return true;
        }
        return false;
    }


     private void saveData() {
        clock.setTimeTrustworthy();
        ObjectOutputStream objoutputStream;
        GregorianCalendar today = new GregorianCalendar();
        today.setTime(new Date());
        try {
            MainActivity.appendLog("TimerCalculationService Data stored");
            objoutputStream = new ObjectOutputStream(getApplicationContext().openFileOutput(filename, Context.MODE_PRIVATE));
            objoutputStream.writeObject(today.get(Calendar.DAY_OF_MONTH));
            objoutputStream.writeObject(this.Calendar.getTime());
            objoutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MainActivity.appendLog("TimerCalculationService Service Stopped.");
        unregisterReceiver(m_DateChangedReceiver);
        unregisterReceiver(mybroadcast);
        isRunning = false;
    }

}
