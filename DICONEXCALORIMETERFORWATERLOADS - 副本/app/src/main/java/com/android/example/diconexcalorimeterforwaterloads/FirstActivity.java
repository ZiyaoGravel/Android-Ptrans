package com.android.example.diconexcalorimeterforwaterloads;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.TwoLineListItem;
import android.widget.ViewFlipper;

import java.util.ArrayList;
import java.util.List;

import Driver.UsbSerialDriver;
import Driver.UsbSerialPort;
import Driver.UsbSerialProber;

/**
 * @author BERTHOMÉ Amélie
 *
 * This class is an activity of the application called DICONEX CALORIMETER FOR WATER LOADS
 * It is used to detect FTDI USB connection. If not it stays in First Activity, if yes it goes in MainActivity. 
 */
public class FirstActivity extends AppCompatActivity {
        //Refreshing devices paremeters
        private static final int MESSAGE_REFRESH = 101;
        private static final long REFRESH_TIMEOUT_MILLIS = 5000;

		//Called by MESSAGE_REFRESH message to refresh the window
        private final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_REFRESH:
                        refreshDeviceList();
                        mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                        break;
                    default:
                        super.handleMessage(msg);
                        break;
                }
            }

        };

		//Use to stock details on the USB Seril Port connected
        private List<UsbSerialPort> mEntries = new ArrayList<UsbSerialPort>();
        private ArrayAdapter<UsbSerialPort> mAdapter;


		//Use to indentify the activity in case of reporting errors
        private final String TAG = MainActivity.class.getSimpleName();
		
		//Variables related to USB
        private UsbManager manager;
        private static UsbSerialPort sPort = null;


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.first_activity);
            
			//Make text blinking
			ViewFlipper mFlipper;
            mFlipper = ((ViewFlipper)findViewById(R.id.flipper));
            mFlipper.startFlipping();
            mFlipper.setInAnimation(AnimationUtils.loadAnimation(getApplicationContext(), android.R.anim.fade_in));
            mFlipper.setOutAnimation(AnimationUtils.loadAnimation(getApplicationContext(), android.R.anim.fade_out));
            
			//Avoid standby
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            
			//Detect USB connection
			manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            mAdapter = new ArrayAdapter<UsbSerialPort>(this, android.R.layout.simple_expandable_list_item_2, mEntries);
        }

        @Override
        protected void onResume() {
            super.onResume();
            mHandler.sendEmptyMessage(MESSAGE_REFRESH);
        }

        @Override
        protected void onPause() {
            super.onPause();
            mHandler.removeMessages(MESSAGE_REFRESH);
        }

		/**
        * When showConsoleActivity is called the MainActivity is called and the first Activity is closed
        * @param port
        */
        private void showConsoleActivity(UsbSerialPort port) {
            MainActivity.show(getApplicationContext(), port);
            finish();
        }

		/**
        * Creating an asynchronous task to detect and memories new USB entries
        */
        private void refreshDeviceList() {

            new AsyncTask<Void, Void, List<UsbSerialPort>>() {
                @Override
                protected List<UsbSerialPort> doInBackground(Void... params) {
                    Log.d(TAG, "Refreshing device list ...");
                    SystemClock.sleep(1000);

                    final List<UsbSerialDriver> drivers =
                            UsbSerialProber.getDefaultProber().findAllDrivers(manager);

                    final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
                    for (final UsbSerialDriver driver : drivers) {
                        final List<UsbSerialPort> ports = driver.getPorts();
                        Log.d(TAG, String.format("+ %s: %s port%s",
                                driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
                        result.addAll(ports);
                    }

                    return result;
                }

                @Override
                protected void onPostExecute(List<UsbSerialPort> result) {
                    mEntries.clear();
                    mEntries.addAll(result);
                    mAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Done refreshing, " + mEntries.size() + " entries found.");
                    if(mEntries.size()!=0) {
                        final UsbSerialPort port = mEntries.get(0);
                        final UsbSerialDriver driver = port.getDriver();
                        final UsbDevice device = driver.getDevice();
                        showConsoleActivity(port);
                    }
                }

            }.execute((Void) null);
        }
}
