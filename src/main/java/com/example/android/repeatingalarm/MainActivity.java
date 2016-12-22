/*
* Copyright 2013 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/


package com.example.android.repeatingalarm;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import com.example.android.common.activities.SampleActivityBase;
import com.example.android.common.logger.Log;
import com.example.android.common.logger.LogFragment;
import com.example.android.common.logger.LogWrapper;
import com.example.android.common.logger.MessageOnlyLogFilter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * A simple launcher activity containing a summary sample description
 * and a few action bar buttons.
 */
public class MainActivity extends SampleActivityBase {
    private static final int REQUEST_CODE = 0;
    public static final String TAG = "MainActivity";
    public static final String FRAGTAG = "RepeatingAlarmFragment";
    private static DevicePolicyManager mDPM;
    private ComponentName mAdminName;
    private static RepeatingAlarmFragment fragment;
    public static final boolean bDebug=true;
    private static boolean mainIsAcive=true;
    private static String dataDir="";
    private String result ="";
    private String password;
    MenuItem awesomeMenuItem;


    private static List<BroadcastReceiver> receivers = new ArrayList<BroadcastReceiver>();
    private Context context;
    private List<PasswordListener> PasswordListenerList = new ArrayList<PasswordListener>();

    public void AddPasswordListner(PasswordListener listner)
    {
        this.PasswordListenerList.add(listner);
    }


    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }


    public Intent myregisterReceiver(BroadcastReceiver receiver, IntentFilter intentFilter){
        if (!myisReceiverRegistered(receiver)){
            receivers.add(receiver);
            Intent intent = this.registerReceiver(receiver, intentFilter);
            appendLog(getClass().getSimpleName() + "registered receiver: "+receiver+"  with filter: "+intentFilter);
            return intent;
        }
        return null;
    }

    public boolean myisReceiverRegistered(BroadcastReceiver receiver){
        boolean registered = receivers.contains(receiver);
        appendLog(getClass().getSimpleName() + "is receiver "+receiver+" registered? "+registered);
        return registered;
    }

    public void myunregisterReceiver(BroadcastReceiver receiver){
        if (myisReceiverRegistered(receiver)){
            receivers.remove(receiver);
            this.unregisterReceiver(receiver);
            appendLog(getClass().getSimpleName() + "unregistered receiver: "+receiver);
        }
    }


    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mainIsAcive)
                finish();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (fragment == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            fragment = new RepeatingAlarmFragment();
            AddPasswordListner(new PasswordListener() {
                @Override
                public void PasswordEntered() {
                    sendBroadcast(new Intent("PasswordRemainingTime"));;
                }
            });
            transaction.add(fragment, FRAGTAG);
            transaction.commit();
            if (Build.VERSION.SDK_INT >= 24) {
                dataDir = getApplicationContext().getDataDir().toString();
            }

            if (bDebug)
                verifyStoragePermissions(this);
            appendLog("mainactivity got permissions");
        }
        if (android.os.Build.VERSION.SDK_INT > 8)
            activateDeviceAdmin();
        if (!isMyServiceRunning(TimeCalculationService.class)) {
            myregisterReceiver(stopReceiver, new IntentFilter("StoppActivity"));

            Intent intent = new Intent(this, TimeCalculationService.class);
            startService(intent);
        }
        mainIsAcive=true;
    }

    @Override
    public void onStart() {
        super.onStart();
        appendLog("Mainactivity OnStart ");
    }

    private void activateDeviceAdmin() {
        if (mDPM==null) {
            try {
                // Initiate DevicePolicyManager.
                mDPM = (DevicePolicyManager) getSystemService(this.getBaseContext().DEVICE_POLICY_SERVICE);
                // Set DeviceAdminDemo Receiver for active the component with different option
                mAdminName = new ComponentName(this, DeviceAdminDemo.class);
                if (!mDPM.isAdminActive(mAdminName)) {
                    // try to become active
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdminName);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Click on Activate button to secure your application.");
                    startActivityForResult(intent, REQUEST_CODE);
                } else {
                    // Already is a device administrator, can do security operations now.
                   //if (android.os.Build.VERSION.SDK_INT<21) //java.lang.SecurityException? || !mDPM.isUninstallBlocked(mAdminName,getPackageName()))
                    ; //todo maybe not necessary       mDPM.lockNow();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }


    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.sample_action) {
            // get prompts.xml view
            LayoutInflater li = LayoutInflater.from(this);
            View promptsView = li.inflate(R.layout.prompts, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

            // set prompts.xml to alertdialog builder
            alertDialogBuilder.setView(promptsView);

            final EditText userInput = (EditText) promptsView
                    .findViewById(R.id.editTextDialogUserInput);

            // set dialog message
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    // get user input and set it to result
                                    // edit text
                                    password =userInput.getText().toString();
                                    if (password.equals("parents")) {
                                        for(PasswordListener listner:PasswordListenerList)
                                        {
                                            listner.PasswordEntered();
                                        }
                                    }
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                }
                            });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();

        }

        return true;
    }

    /** Create a chain of targets that will receive log data */
    @Override
    public void initializeLogging() {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);

        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);

        // On screen logging via a fragment with a TextView.
        LogFragment logFragment = (LogFragment) getSupportFragmentManager()
                .findFragmentById(R.id.log_fragment);
        msgFilter.setNext(logFragment.getLogView());
        logFragment.getLogView().setTextAppearance(this, R.style.Log);
        logFragment.getLogView().setBackgroundColor(Color.WHITE);
    }

    @Override
    public void finish() {
        //super.finish(); // do not call super
        appendLog("Mainactivity OnFinish ");
        moveTaskToBack(true); // move back
        mainIsAcive=false;
    }

    /**
     * @author Prashant Adesara
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(REQUEST_CODE == requestCode)
        {
            if(requestCode == Activity.RESULT_OK)
            {
                // done with activate to Device Admin
            }
            else
            {
                // cancle it.
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        appendLog("Mainactivity OnStop ");
    }

    @Override
    public void onPause() {
        super.onPause();
        mainIsAcive=false;
        appendLog("Mainactivity OnPause ");
    }

    @Override
    public void onRestart() {
        appendLog("Mainactivity OnRestart ");
        mainIsAcive=true;
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        myunregisterReceiver(stopReceiver);

        appendLog("MainActivity -> Destroyed");
    }


    public static void appendLog(String text)
    {
        if (!bDebug)
            return;
        //      File logFile = new File(getApplicationContext().getFilesDir().getPath()+"timecalc.log");
        File logFile = new File(Environment.getExternalStorageDirectory().getAbsoluteFile()+dataDir , "mainactivity.txt");

        //File logFile = new File("/sdcard/timecalc.log");
        logFile.setReadable(true,false);
        String s = logFile.getAbsolutePath();
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            Calendar c = Calendar.getInstance();
            buf.append(DateFormat.format("yyyy.MM.dd G 'at' HH:mm:ss ", c.getTime()) + text + "\n");
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
