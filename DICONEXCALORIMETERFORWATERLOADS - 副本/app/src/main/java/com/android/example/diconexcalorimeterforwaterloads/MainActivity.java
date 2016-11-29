package com.android.example.diconexcalorimeterforwaterloads;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import MBUS.*;
import Driver.*;


/**
 * @authors BERTHOMÉ Amélie & LI Ziyao
 * This class is an activity of the application called DICONEX CALORIMETER FOR WATER LOADS
 * It is used to detect data from MBUS Calorimeter link and to print it.
 *
 * Data comes from the USB port that's why the Android's mobile or tablet need to grant USB permission.
 * The permission is asked with transparency for the user.
 * When the phone or tablet detects the granted permission it goes in the Broadcast Receiver.
 * The Broadcast Receiver is used to receive data from the Calorimeter.
 * When data is detected it is saved in a text file called MBUS_Data into the repertory named Diconex_water_loads.
 * Then the file is read to provide the needed information to the activity.
 *
 */
 
public class MainActivity extends AppCompatActivity {
    //Variables associated to the layout
    private ImageButton button;
    private TextView powertext;
    private TextView flowtext;
    private TextView hottemptext;
    private TextView coldtemptext;
    private TextView deltatemptext;

    //Variables related to the USB port
    private UsbManager manager;
    private UsbDevice device;
    private static UsbSerialPort sPort = null;
    private UsbManager usbManager;
    private UsbDevice usbDevice = null;
    private UsbInterface usbInterface = null;
    UsbEndpoint usbEpRead = null;
    UsbEndpoint usbEpWrite = null;
    UsbDeviceConnection usbConnection;
    
    //Variables related to the permission
    private static String ACTION_USB_PERMISSION = "MainActivity";
    private IntentFilter filter;
    private PendingIntent mPermissionIntent;

    //Variables related to the MBUS protocol
    private int MBUS_CALORIMETER_MESSAGE_LENGTH=115;
    private boolean[] frameCountBits;
    private int slaveAddr =0;
    public boolean firstTime = true;
    private ByteBuffer buffer = ByteBuffer.allocate(255);
    private int [] messTab = new int[2];
    private VariableDataStructure variableDataStructure;
    
    //Definition of a BroadcastReceiver variable
    private MyBroadcastReceiver mUsbReceiver;

    //Stocks the instance of the activity (used to update the layout)
    private static MainActivity ins;

    /**
     * Get the instance of the activity
     * @return
     */
    public static MainActivity getInstance(){
        return ins;
    }


   
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ins = this;
        setContentView(R.layout.main_activity);

        //setTitle, it needs to use before setContent.
        setTitle("DICONEX CALORIMETER FOR WATER LOADS");

        //setContentView, call the main layout
        setContentView(R.layout.main_activity);

        //hide the title bar automatic
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //Linked the variables to the layout (get the areas of the layout which wil be updated)
        powertext = (TextView)findViewById(R.id.powerdata);
        coldtemptext = (TextView)findViewById(R.id.colddata);
        flowtext = (TextView)findViewById(R.id.flowdata);
        hottemptext = (TextView)findViewById(R.id.hotdata);
        deltatemptext = (TextView)findViewById(R.id.deltadata);

        //Get the help button
        button = (ImageButton) findViewById(R.id.btn_test_popupwindow);


        /*//change the words' color,if the power higher than limit, than the word will change the color.

        if(Float.parseFloat(powertext.getText().toString())>=50){
            powertext.setTextColor(getResources().getColor(R.color.red));
            coldtemptext.setTextColor(getResources().getColor(R.color.red));
            hottemptext.setTextColor(getResources().getColor(R.color.red));
            flowtext.setTextColor(getResources().getColor(R.color.red));
            deltatemptext.setTextColor(getResources().getColor(R.color.red));
        }*/


        //Use to listen to new data each 3 seconds
        update();

        //listener for help button and go to the help page
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, HelpActivity.class);//turn to the second activity
                startActivity(intent);

            }
        });
    }

    
    /**
     * update() is used to call the Broadcast Receiver each 3 seconds to update received data
     */
    private void update() {
        Timer timer = new Timer();
        //Called every 3 seconds
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                powertext = (TextView) findViewById(R.id.powerdata);
                powertext.post(new Runnable() {
                    @Override
                    public void run() {
                        //The first time we need to declare the filter (listening for USB permission to come)
                        if(firstTime == true){
                            firstTime = false;
                            manager = (UsbManager) getSystemService(Context.USB_SERVICE);
                            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
                            device = deviceList.get("/dev/bus/usb/001/002");

                            //Declare the intent and the filter
                            mPermissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
                            filter = new IntentFilter(ACTION_USB_PERMISSION);

                            mUsbReceiver = new MyBroadcastReceiver();

                            //Call the Broadcast Receiver
                            registerReceiver(mUsbReceiver, filter);
                            manager.requestPermission(device, mPermissionIntent);
                        }
                        else{
                            //If it's not the first time the function is called we need to unregister the BroadcastReceiver and recreate it
                            unregisterReceiver(mUsbReceiver);
                            registerReceiver(mUsbReceiver, filter);
                            manager.requestPermission(device, mPermissionIntent);
                        }
                    }
                });
            }
        }, 0, 3000);//Update data every 3 seconds

    }


    /**
     * updateTheTextView is called to update the data printed with the new data just received from the USB link
     * @param myTextView
     * @param updatedData
     */
    public void updateTheTextView(final TextView myTextView, final String updatedData) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                myTextView.setText(updatedData);
            }
        });
    }

    
    /**
     * Special class used to receive asynchronously data
     */
    public class MyBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                        //Get USB parameter used
                        usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

                        //Fetch all the endpoints
                        setupConnection();

                        //Open and claim interface
                        usbConnection = usbManager.openDevice(usbDevice);
                        usbConnection.claimInterface(usbInterface, true);

                        //Set dtr to true (ready to accept data)
                        usbConnection.controlTransfer(0x21, 0x22, 0x1, 0, null, 0, 0);
                        
                        //Read calorimeter answer
                        messTab = reading();
                        int len = messTab[0];
                        int messLength = messTab[1];
                        //Toast.makeText(getApplicationContext(), "FIRST Len=" + len, Toast.LENGTH_SHORT).show();
                        //Toast.makeText(getApplicationContext(), "FIRST MSG Len=" + messLength, Toast.LENGTH_SHORT).show();
                        
                        //Update the printed data  if the message is detected as a calorimeter correct answer
                        if(messLength == MBUS_CALORIMETER_MESSAGE_LENGTH)
                            printModification(messLength);
                        
                        try {
                            sPort.open(usbConnection);
                            
                            //Set the paramters of the link (baudrate, stop bits, parity)
                            sPort.setParameters(2400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_EVEN);
                            
                            //Initialize frameCountsBits
                            frameCountBits = new boolean[255];
                            for (int i = 0; i < frameCountBits.length; i++) {
                                frameCountBits[i] = true;
                            }
                            
                            //Send init frame
                            init();
                            
                            //Read calorimeter answer
                            messTab = reading();
                            len = messTab[0];
                            messLength = messTab[1];
                            //Toast.makeText(getApplicationContext(), "INIT Len=" + len, Toast.LENGTH_SHORT).show();
                            //Toast.makeText(getApplicationContext(), "INIT MSG Len=" + messLength, Toast.LENGTH_SHORT).show();
                            
                            //Update the printed data if the message is detected as a calorimeter correct answer
                            if(messLength == MBUS_CALORIMETER_MESSAGE_LENGTH)
                                printModification(messLength);
                            
                            //Is this is not the first time communicating send special MBUS frame
                            if (frameCountBits[slaveAddr]) {
                                sendShortMessage7b();
                                frameCountBits[slaveAddr] = false;
                            }
                            //Is this is the first time communicating sen special MBUS frame
                            else {
                                sendShortMessage5b();
                                frameCountBits[slaveAddr] = true;
                            }

                            //Read calorimeter answer
                            messTab = reading();
                            len = messTab[0];
                            messLength = messTab[1];
                            //Toast.makeText(getApplicationContext(), "DATA Len=" + len, Toast.LENGTH_SHORT).show();
                            //Toast.makeText(getApplicationContext(), "DATA MSG Len=" + messLength, Toast.LENGTH_SHORT).show();
                            
                            //Update the printed data if the message is detected as a calorimeter correct answer
                            if(messLength == MBUS_CALORIMETER_MESSAGE_LENGTH)
                                printModification(messLength);

                        } catch (IOException e) {
                            //Toast.makeText(getApplicationContext(), "SETTING ERROR", Toast.LENGTH_LONG).show();
                            Log.e(this.getClass().getSimpleName(), "SETTING ERROR : "+e.getMessage());
                        }
                        
                        //Close the port at the end of the communication process
                        try {
                            sPort.close();
                        } catch (IOException e) {
                            //Toast.makeText(getApplicationContext(), "ERROR CLOSING sPort", Toast.LENGTH_LONG).show();
                            Log.e(this.getClass().getSimpleName(), "ERROR CLOSING PORT : "+e.getMessage());
                        }
                        usbConnection.close();
                    }
                }
            }

        }

        /**
         * printModification is used to print the new data on the screen
         * @param messLength
         */
        public void printModification(int messLength){
            try {
                //Get the received data into a byte array
                byte[] buf = new byte[messLength];
                for (int k = 0; k < messLength; k++)
                    buf[k] = buffer.get(k);
                
                //Put the data into a VariableDataStructure to be decoded properly
                variableDataStructure = new VariableDataStructure(buf, 8, messLength - 8, null, null);
                variableDataStructure.decode();
                
                //Converting received data into String
                String data;
                data = variableDataStructure.toString();
                Write(data);
                String[] myData = findData();
                
                //Try to modify the layout with the new data
                try {
                    powertext = (TextView) findViewById(R.id.powerdata);
                    flowtext = (TextView) findViewById(R.id.flowdata);
                    hottemptext = (TextView) findViewById(R.id.hotdata);
                    coldtemptext = (TextView) findViewById(R.id.colddata);
                    deltatemptext = (TextView) findViewById(R.id.deltadata);

                    MainActivity.getInstance().updateTheTextView(powertext, myData[0]);
                    MainActivity.getInstance().updateTheTextView(flowtext, myData[1]);
                    MainActivity.getInstance().updateTheTextView(hottemptext, myData[2]);
                    MainActivity.getInstance().updateTheTextView(coldtemptext, myData[3]);
                    MainActivity.getInstance().updateTheTextView(deltatemptext, myData[4]);
                } catch (Exception e) {
                    //Toast.makeText(getApplicationContext(), "ERROR UPDATING : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(this.getClass().getSimpleName(), "ERROR UPDATING : "+e.getMessage());
                }
            } catch (DecodingException de) {
                //Toast.makeText(getApplicationContext(), "ERROR DECODING : " + de, Toast.LENGTH_SHORT).show();
                Log.e(this.getClass().getSimpleName(), "ERROR DECODING : " + de.getMessage());
            }
        }
    };


	/**
     * findData is used to get the right data in the text file which stocks the received frame decoded
     * @return
     */
    public String[] findData()
    {
        //Variables that will stock the data searched in the file
        String flow=new String();
        String power=new String();
        String tempH=new String();
        String tempC=new String();
        String deltaT=new String();

		//Declare the file which contains the MBUS decoded frame
        File file = new File(Environment.getExternalStorageDirectory() +
                File.separator + "Diconex_water_loads","MBUS_data.txt");

        if (file.exists()) {
            TabFileReader.readTextFile(file.getAbsolutePath(),' ',"");
            for(int i=0; i<TabFileReader.ncol();i++){
                //Get flow value
                if((TabFileReader.words[4][i]!=null) && (TabFileReader.words[4][i].equals("unit:CUBIC_METRE_PER_HOUR"))){
                    for(int j=0; j<TabFileReader.words[4][i-1].length(); j++){
                        String mot=TabFileReader.words[4][i-1];
                        int taille = TabFileReader.words[4][i-1].length();
                        if(mot.charAt(j)==':'){
                            flow=mot.substring(j+1,taille-1 );
                        }
                    }
                }
                
				//Get power value
                if((TabFileReader.words[5][i]!=null) && (TabFileReader.words[5][i].equals("unit:WATT"))){
                    for(int j=0; j<TabFileReader.words[5][i-1].length(); j++){
                        String mot=TabFileReader.words[5][i-1];
                        int taille = TabFileReader.words[5][i-1].length();
                        if(mot.charAt(j)==':'){
                            power=mot.substring(j+1,taille-1 );
                        }
                    }
                }
                
				//Get hot temperature value
                if((TabFileReader.words[6][i]!=null) && (TabFileReader.words[7][i].equals("unit:DEGREE_CELSIUS"))){
                    for(int j=0; j<TabFileReader.words[6][i-1].length(); j++){
                        String mot=TabFileReader.words[6][i-1];
                        int taille = TabFileReader.words[6][i-1].length();
                        if(mot.charAt(j)==':'){
                            tempH=mot.substring(j+1,taille-1);
                        }
                    }
                }
				
                //Get cold temperature value
                if((TabFileReader.words[7][i]!=null) && (TabFileReader.words[7][i].equals("unit:DEGREE_CELSIUS"))){
                    for(int j=0; j<TabFileReader.words[7][i-1].length(); j++){
                        String mot=TabFileReader.words[7][i-1];
                        int taille = TabFileReader.words[7][i-1].length();
                        if(mot.charAt(j)==':'){
                            tempC=mot.substring(j+1,taille-1);
                        }
                    }
                }

                /*
                //Get delta temperature value
                if((TabFileReader.words[8][i]!=null) && (TabFileReader.words[8][i].equals("unit:KELVIN"))){
                    for(int j=0; j<TabFileReader.words[8][i-1].length(); j++){
                        String mot=TabFileReader.words[8][i-1];
                        int taille = TabFileReader.words[8][i-1].length();
                        if(mot.charAt(j)==':'){
                            deltaT=mot.substring(j+1,taille-1);
                        }
                    }
                }
                */
            }
        }
        else{
            Toast.makeText(getApplicationContext(), "FICHIER INTROUVABLE", Toast.LENGTH_SHORT).show();
        }

        //Define print format
        DecimalFormat powerFormat = new DecimalFormat("00.00");
        DecimalFormat flowFormat = new DecimalFormat("0.00");
        DecimalFormat hotTempFormat = new DecimalFormat("00.0");
        DecimalFormat coldTempFormat = new DecimalFormat("00.0");
        DecimalFormat deltaTempFormat = new DecimalFormat("00.0");

        //Converting float into double
        Double dPowerW = Double.parseDouble(power);
        Double dPowerKW = dPowerW/1000;
        Double dFlow = Double.parseDouble(flow);
        Double dHotTemp = Double.parseDouble(tempH);
        Double dColdTemp = Double.parseDouble(tempC);
        Double dDeltaTemp = dHotTemp-dColdTemp;
        
        //Return data into String array
		String[] toPrint = new String[5];
		toPrint[0]=powerFormat.format(dPowerKW);
        toPrint[1]=flowFormat.format(dFlow);
        toPrint[2]=hotTempFormat.format(dHotTemp);
        toPrint[3]=coldTempFormat.format(dColdTemp);
        toPrint[4]=deltaTempFormat.format(dDeltaTemp);
        
		return toPrint;
    }

	
    /**
     * setupConnection() find the endpoints that will be used for the communication
     */
    private void setupConnection()
    {
        //Find the right interface
        for(int i = 0; i < usbDevice.getInterfaceCount(); i++)
        {
            //Communications device class (CDC) type device
            if(usbDevice.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC)
            {
                usbInterface = usbDevice.getInterface(i);

                //Find the endpoints
                for(int j = 0; j < usbInterface.getEndpointCount(); j++)
                {
                    if(usbInterface.getEndpoint(j).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)
                    {
                        if(usbInterface.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_OUT)
                        {
                            //From host to device
                            usbEpWrite = usbInterface.getEndpoint(j);
                            //Toast.makeText(getApplicationContext(), "USB_DIR_OUT :"+usbEpWrite, Toast.LENGTH_SHORT).show();
                        }

                        if(usbInterface.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_IN)
                        {
                            //From device to host
                            usbEpRead = usbInterface.getEndpoint(j);
                            //Toast.makeText(getApplicationContext(), "USB_DIR_IN:"+usbEpRead, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }
    }

	/**
     * reading() is used to read data from the reading endpoint
     * @return
     */
    private int[] reading(){
        //Re/Initialise the buffer
        byte[] bytes = new byte[255];
        Arrays.fill(bytes, (byte) 0);
        buffer=ByteBuffer.wrap(bytes);
        int readByte=0;
        int messageLength=0;
        int [] tab= new int [2];
        String dataByte, data = "";

        //Define the request
        UsbRequest request = new UsbRequest();
        request.initialize(usbConnection, usbEpRead);

        //Queue a request on the interrupt endpoint
        request.queue(buffer, buffer.capacity());
        //Wait for status event
        if(usbConnection.requestWait() == request)
        {
            //There is no way to know how many bytes are coming, so simply forward the non-null values
            for(int i = 0; i < buffer.capacity() ; i++)
            {
                //Transform ascii (0-255) to its character equivalent and append
                dataByte = Character.toString((char) buffer.get(i));
                data +=dataByte;
                Log.e(this.getClass().getSimpleName(), "Was not able to read from USB device, ending listening thread ----> "+ data);
                readByte++;
                
                //If calorimeter answers 0xE5, the length is only 1 byte (refer to the MBUS protocol)
                if ((buffer.get(2) & 0xff) == 0xe5) {
                    messageLength = 1;
                }
                //If calorimeter answers 0x68, the length of the frame is define at the 3rd position (refer to the MBUS protocol)
                else if ((buffer.get(2) & 0xff) == 0x68) {
                    messageLength = (buffer.get(3) & 0xff) + 6;
                }
                //Stop reading when everything is read
                if (readByte == messageLength) {
                    break;
                }
            }
            tab[0] = readByte;
            tab[1] = messageLength;
            return tab;
        }
        return tab;
    }


    /**
     * init() send the initialisation frame to the calorimeter
     * @throws IOException
     */
    private void init() throws IOException {
        int offset;
        byte outputBuffer[]=new byte[5];
        outputBuffer[0] = 0x10;
        outputBuffer[1] = 0x40;
        outputBuffer[2] = 0x00; //slaveAddr
        outputBuffer[3] = 0x40;
        outputBuffer[4] = 0x16;
        offset=sPort.write(outputBuffer, 5000);
        frameCountBits[0] = true;
    }

    /**
     * sendShortMessage7b() send 0x7B to the calorimeter (refer to MBUS protocol)
     * @throws IOException
     */
    private void sendShortMessage7b() throws IOException {
        int offset;
        byte outputBuffer[]=new byte[5];
        outputBuffer[0] = 0x10;
        outputBuffer[1] = 0x7b;
        outputBuffer[2] = 0x00; //slaveAddr
        outputBuffer[3] = 0x7b;
        outputBuffer[4] = 0x16;
        offset=sPort.write(outputBuffer, 5000);
        //Toast.makeText(getApplicationContext(), "Written:"+outputBuffer[3], Toast.LENGTH_SHORT).show();
    }

    /**
     * sendShortMessage5b() send 0x5B to the calorimeter (refer to MBUS protocol)
     * @throws IOException
     */
    private void sendShortMessage5b() throws IOException {
        int offset;
        byte outputBuffer[]=new byte[5];
        outputBuffer[0] = 0x10;
        outputBuffer[1] = 0x5b;
        outputBuffer[2] = 0x00; //slaveAddr
        outputBuffer[3] = 0x5b;
        outputBuffer[4] = 0x16;
        offset=sPort.write(outputBuffer, 5000);
        //Toast.makeText(getApplicationContext(), "Written:"+outputBuffer[3], Toast.LENGTH_SHORT).show();
    }


    /**
     * Write() write the received data to the file text
     * @param data
     */
    public void Write(String data){
        //Declare the file text
		File file = new File(Environment.getExternalStorageDirectory() +
                File.separator + "Diconex_water_loads","MBUS_data.txt"); //on déclare notre futur fichier

		//Declare the file directory
        File myDir = new File(Environment.getExternalStorageDirectory() +
                File.separator + "Diconex_water_loads");
        Boolean success=true;
        if (!myDir.exists()) {
			//Create the repertory if it doesn't exist
            success = myDir.mkdir();
        }

        if (success){

            try{
				//Write in the file text the data (erase the file if there is data in it)
                FileOutputStream output = new FileOutputStream(file,false);
                output.write(data.getBytes());
            }catch(IOException ioe) {
                Log.e("TEST1", "ERRO WRITING FILE");
            }
        }
        else {Log.e("TEST1", "ERROR CREATE REPERTORY");}
    }


     /**
     * show() is called when FirstActivity starts MainActivity.
     * Starts the activity, using the supplied driver instance.
     * @param context
     * @param port
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

    }
}