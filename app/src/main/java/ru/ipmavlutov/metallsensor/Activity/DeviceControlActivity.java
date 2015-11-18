package ru.ipmavlutov.metallsensor.Activity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import ru.ipmavlutov.metallsensor.DeviceConnector;
import ru.ipmavlutov.metallsensor.DeviceData;
import ru.ipmavlutov.metallsensor.Graphs.Graph;
import ru.ipmavlutov.metallsensor.R;

public class DeviceControlActivity extends MainActivity {
    private static final String DEVICE_NAME = "DEVICE_NAME";
    private static String MSG_NOT_CONNECTED;
    private static String MSG_CONNECTING;
    private static String MSG_CONNECTED;
    public String MAC_ADDRESS;


    public TextView temperature_text_1;
    public TextView temperature_value_1;
    public TextView signal_text_1;
    public TextView signal_value_1;


    private static DeviceConnector connector;
    private static BluetoothResponseHandler mHandler;
    private boolean change_menu;



    private EditText editText;
    private EditText editText2;
    private double Z1;
    private double Z2;
    private String p0_value1;
    private String p0_value2;
    private TextView absolute_value_1;
    private TextView temperature_value_2;
    private TextView signal_value_2;
    private TextView absolute_value_2;

    DBHelper dbHelper;
    Timer tm;
    MyTimerTask myTT;

    private String DB_PATH;
    private static String DB_NAME;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor sharedPreferencesEditor;
    private static final int PREFERENCE_MODE_PRIVATE = 0;




    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (mHandler == null) mHandler = new BluetoothResponseHandler(this);
        else mHandler.setTarget(this);
        MSG_NOT_CONNECTED = getString(R.string.msg_not_connected);
        MSG_CONNECTING = getString(R.string.msg_connecting);
        MSG_CONNECTED = getString(R.string.msg_connected);
        change_menu = false;
        DB_NAME = "myDB";

      /*  try {
            writeToSD();
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        dbHelper = new DBHelper(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            DB_PATH = getBaseContext().getFilesDir().getAbsolutePath().replace("files", "databases") + File.separator;
        } else {
            DB_PATH = getBaseContext().getFilesDir().getPath() + getBaseContext().getPackageName() + "/databases/";
        }


        if (isConnected() && (savedInstanceState != null)) {
            setDeviceName(savedInstanceState.getString(DEVICE_NAME));
        } else
            getSupportActionBar().setSubtitle(MSG_NOT_CONNECTED);
    }

    private boolean isConnected() {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }

    // ==========================================================================
    private void stopConnection() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }
    }

    // ==========================================================================
    private void writeToSD() throws IOException {
        File sd = Environment.getExternalStorageDirectory();

        if (sd.canWrite()) {
            String currentDBPath = DB_NAME;
            String backupDBPath = "backupname.db";
            File currentDB = new File(DB_PATH, currentDBPath);
            File backupDB = new File(sd, backupDBPath);

            if (currentDB.exists()) {
                FileChannel src = new FileInputStream(currentDB).getChannel();
                FileChannel dst = new FileOutputStream(backupDB).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
            }
        }
    }

    /**
     * Список устройств для подключения
     */
    private void startDeviceListActivity() {
        stopConnection();
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    // ============================================================================
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == RESULT_OK) {


                    String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    MAC_ADDRESS = address;

                    BluetoothDevice device = btAdapter.getRemoteDevice(address);
                    if (super.isAdapterReady() && (connector == null)) setupConnector(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                super.pendingRequestEnableBt = false;
                break;
        }
    }

    // ==========================================================================
    private void setupConnector(BluetoothDevice connectedDevice) {
        stopConnection();
        try {
            String emptyName = getString(R.string.empty_device_name);
            DeviceData data = new DeviceData(connectedDevice, emptyName);
            connector = new DeviceConnector(data, mHandler);
            connector.connect();
        } catch (IllegalArgumentException | IOException ignored) {

        }
    }

    // ==========================================================================
    void setDeviceName(String deviceName) {
        //noinspection ConstantConditions
        getSupportActionBar().setSubtitle(deviceName);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (change_menu) {
            getMenuInflater().inflate(R.menu.menu_device_control, menu);

        } else {
            getMenuInflater().inflate(R.menu.menu_main, menu);
        }
        return true;
    }


    // ==========================================================================

    /**
     * Обработчик приёма данных от bluetooth-потока
     */
    public class BluetoothResponseHandler extends Handler {


        private WeakReference<DeviceControlActivity> mActivity;


        public BluetoothResponseHandler(DeviceControlActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        public void setTarget(DeviceControlActivity target) {
            mActivity.clear();
            mActivity = new WeakReference<>(target);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(Message msg) {
            DeviceControlActivity activity = mActivity.get();
            if (activity != null) {


                switch (msg.what) {

                    case MESSAGE_STATE_CHANGE:


                        final ActionBar bar = activity.getSupportActionBar();


                        switch (msg.arg1) {


                            case DeviceConnector.STATE_CONNECTED:

                                assert bar != null;
                                bar.setSubtitle(MSG_CONNECTED);

                                change_activity();
                                tm = new Timer();
                                myTT = new MyTimerTask();

                                tm.schedule(myTT, 3000, 1200000);


                            case DeviceConnector.STATE_CONNECTING:
                                assert bar != null;
                                bar.setSubtitle(MSG_CONNECTING);
                                break;
                            case DeviceConnector.STATE_NONE:

                                // assert bar != null;
                                assert bar != null;
                                bar.setSubtitle(MSG_NOT_CONNECTED);
                                if (change_menu) {
                                    myTT.cancel();
                                    tm.cancel();
                                }
                                change_menu = false;
                                invalidateOptionsMenu();
                                setContentView(R.layout.activity_main);


                                break;

                        }
                        break;
                    case TEMPRETURE:

                        // GetTempereture(msg);

                        break;

                    case MESSAGE_READ:
                        /*final String readMessage = (String) msg.obj;
                        if (readMessage != null) {
                            activity.appendLog(readMessage, false, false, activity.needClean);
                        }*/
                        break;

                    case MESSAGE_DEVICE_NAME:
                        activity.setDeviceName((String) msg.obj);
                        break;

                    case MESSAGE_WRITE:
                        // stub
                        break;

                    case MESSAGE_TOAST:
                        Toast.makeText(DeviceControlActivity.this, "Устройство " + msg.obj + " отключено", Toast.LENGTH_LONG).show();
                        setContentView(R.layout.activity_main);


                        break;

                    case SIGNAL:
                       // GetSignal(msg);
                        break;
                    case SUPERSIGNAL:
                        byte[] a = (byte[]) msg.obj;
                        if (a.length == 19) {
                            byte[] temperature_1 = Arrays.copyOfRange(a, 0, 2);
                            byte[] signal_1 = Arrays.copyOfRange(a, 2, 7);
                            byte[] temperature_2 = Arrays.copyOfRange(a, 7, 9);
                            byte[] signal_2 = Arrays.copyOfRange(a, 9, 14);


                            //температура датчиков
                            short temperature_rezult_1 = GetTemperature(temperature_1);
                            temperature_value_1.setText(String.valueOf(temperature_rezult_1) + "  " + "\u00b0" + "C");
                            get_temperature1 = temperature_rezult_1;
                            short temperature_rezult_2 = GetTemperature(temperature_2);
                            temperature_value_2.setText(String.valueOf(temperature_rezult_2) + "  " + "\u00b0" + "C");
                            get_temperature2 = temperature_rezult_2;
                            //значение частиц
                            // double signal_rezult_1 = FindRudeSignal(Correction(GetSignal(signal_1), temperature_rezult_1), Z1);
                            // signal_value_1.setText(Double.toString(signal_rezult_1)+" " + "мг");
                            try {
                                signal_value_1.setText(new String(signal_1, "UTF-8") + " " + "мг");
                                signal_value_2.setText(new String(signal_2, "UTF-8") + " " + "мг");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                            // get_signal1 = signal_rezult_1;
                            // double signal_rezult_2 = FindRudeSignal(Correction(GetSignal(signal_2), temperature_rezult_2), Z2);
                            // signal_value_2.setText(Double.toString(signal_rezult_2)+" " + "мг");
                            // get_signal2 = signal_rezult_2;

                            // String string = new String(a, 0, msg.arg1);
                            // signal_text_1.setText(Arrays.toString(a));
                            //GetSuperSignal(msg);
                            break;
                        }

                }

            }

        }

        private void change_activity() {
            setContentView(R.layout.activity_work);


            sharedPreferences = getPreferences(PREFERENCE_MODE_PRIVATE);
            Z1 = sharedPreferences.getFloat("Z1", (float) Z1);
            Z2 = sharedPreferences.getFloat("Z2", (float) Z2);

            //relativeLayout1
            temperature_text_1 = (TextView) findViewById(R.id.temperature_text_1);
            temperature_value_1 = (TextView) findViewById(R.id.temperature_value_1);
            signal_text_1 = (TextView) findViewById(R.id.signal_text_1);
            signal_value_1 = (TextView) findViewById(R.id.signal_value_1);
            //TextView absolute_text_1 = (TextView) findViewById(R.id.absolute_text_1);
           // TextView current_value_label = (TextView) findViewById(R.id.current_value_label);
            absolute_value_1 = (TextView) findViewById(R.id.absolute_value_1);
            editText = (EditText) findViewById(R.id.editText);
            Button abs_btn = (Button) findViewById(R.id.absolute_btn);

            //RelativeLayout2
          //  TextView temperature_text_2 = (TextView) findViewById(R.id.temperature_text_2);
            temperature_value_2 = (TextView) findViewById(R.id.temperature_value_2);
           // TextView signal_text_2 = (TextView) findViewById(R.id.signal_text_2);
            signal_value_2 = (TextView) findViewById(R.id.signal_value_2);
           // TextView absolute_text_2 = (TextView) findViewById(R.id.absolute_text_2);
            //current_value_label = (TextView) findViewById(R.id.current_value_label);
            absolute_value_2 = (TextView) findViewById(R.id.absolute_value_2);
            editText2 = (EditText) findViewById(R.id.editText2);
            Button abs_btn2 = (Button) findViewById(R.id.absolute_btn2);

            // supersigntext = (TextView) findViewById(R.id.current_value_label);



            if (Z1 == 0.0) {
                Z1=96.0;
                absolute_value_1.setText(Double.toString(Z1));
            } else {
                absolute_value_1.setText(Double.toString(Z1));
            }
            if (Z2 == 0.0) {
                Z2=96.0;
                absolute_value_2.setText(Double.toString(Z2));
            } else {
                absolute_value_2.setText(Double.toString(Z2));
            }


            View.OnClickListener abs_click = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sharedPreferences = getPreferences(PREFERENCE_MODE_PRIVATE);
                    sharedPreferencesEditor = sharedPreferences.edit();


                    switch (v.getId()) {
                        case R.id.absolute_btn:
                            p0_value1 = editText.getText().toString();

                            if (p0_value1.isEmpty()) {
                                Z1 = sharedPreferences.getFloat("Z1", (float) Z1);

                                absolute_value_1.setText(Double.toString(Z1));
                            } else {
                                Z1 = Double.parseDouble(p0_value1);
                                absolute_value_1.setText(Double.toString(Z1));
                                sharedPreferencesEditor.putFloat("Z1", (float) Z1);
                            }
                            break;
                        case R.id.absolute_btn2:
                            p0_value2 = editText2.getText().toString();

                            if (p0_value2.isEmpty()) {
                                Z2 = sharedPreferences.getFloat("Z1", (float) Z2);
                                absolute_value_2.setText(Double.toString(Z2));
                            } else {
                                Z2 = Double.parseDouble(p0_value2);
                                absolute_value_2.setText(Double.toString(Z2));
                                sharedPreferencesEditor.putFloat("Z2", (float) Z2);
                            }
                            break;

                    }
                    sharedPreferencesEditor.apply();

                }
            };
            abs_btn.setOnClickListener(abs_click);
            abs_btn2.setOnClickListener(abs_click);

            change_menu = true;
            invalidateOptionsMenu();
            supportInvalidateOptionsMenu();


        }



        private double GetSignal(byte[] signal_array) {
            double signal;
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.put(signal_array[0]);
            bb.put(signal_array[1]);
            signal = bb.getShort(0);
            return signal;


        }


        //SQLiteOpenHelper dbHelper;


    }

    private short GetTemperature(byte[] temperature_array) {
        short temperature;
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put(temperature_array[0]);
        bb.put(temperature_array[1]);
        temperature = bb.getShort(0);
        return temperature;
        //s = String.valueOf(get_temperature1);
        //Log.d("TAG", s);
       /* int temperature; // принятое значение температуры
        byte b[]; //
        // short input; // температура, которую отправляет прибор
        // 130 = 0x0082, старший байт 0х00, младший 0х82 = -126, т.к. число со знаком( > 127)
        //input = 130;// в таком виде температура приходит на смартфон
        //  b = new byte[]{(byte) (input >> 8), (byte) (input & 0xff)};// Преобразование двух байт в short с учетом знака

        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put(temperature_1[0]);
        bb.put(temperature_1[1]);
        temperature = bb.getInt(0);
        temptext.setText(String.valueOf(temperature) + "  " + "\u00b0" + "C");*/
    }


    // ==========================================================================


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_search:
                if (super.isAdapterReady()) {
                    if (isConnected()) stopConnection();
                    else startDeviceListActivity();
                } else {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                return true;
            case R.id.menu_graph:
                Intent openGraph = new Intent(getBaseContext(), Graph.class);
                startActivity(openGraph);


            default:
                return super.onOptionsItemSelected(item);
        }
    }
    // ============================================================================

    /**
     * ***************Signal*****************************************
     */


    double m;
    double dp;
    double result;

    public double FindRudeSignal(double P,double Z) {

        dp = P - Z;//104
        if (dp < 3) {
            m = 0;
        } else {
            if (dp < 9) {//8
                m = 0.075 * dp;

            } else {
                if (dp < 27) {
                    m = 0.0473 * dp + 0.2229;
                } else {
                    if (dp < 51) {
                        m = 0.10416 * dp - 1.3121;
                    } else {
                        m = 0.375 * dp - 15.125;
                    }
                }
            }
        }

        result = new BigDecimal(m).setScale(1, RoundingMode.UP).doubleValue();
        return (result);
    }

    /**
     * ********************************************************
     */
    /**
     * ***************SuperSignal*****************************************
     */

    /*final int SP0 = 533;
    double super_result;

    public double FindSuperSignal(int P) {

        dp = Math.abs(SP0 - P);
        if (dp < 85) {
            m = 0;
        } else {
            if (dp < 270) {
                m = 0.00486 * dp + 0.187;
            } else {
                if (dp < 510) {
                    m = 0.010476 * dp - 1.3123;
                } else {
                    m = 0.0375 * dp - 15.125;
                }
            }
        }

        super_result = new BigDecimal(m).setScale(1, RoundingMode.UP).doubleValue();
        return (super_result);
    }*/


    double correct_signal;

    /**
     * ***************Correction*****************************************
     */
    public double Correction(double P,short temperature) {
        double correction;
        if (temperature < 76) {
            correction = 0.45 * temperature - 11.25;


        } else {
            correction = 0.29 * temperature + 0.96;

        }
        correct_signal = P - correction;
        return correct_signal;
    }
    @Override
    protected void onDestroy() {

        super.onDestroy();
        try {
            writeToSD();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static class DBHelper extends SQLiteOpenHelper {


        private static final String LOG_TAG = "DB";

        public DBHelper(Context context) {
            // конструктор суперкласса
            super(context, DB_NAME, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(LOG_TAG, "--- onCreate database ---");
            // создаем таблицу с полями

            db.execSQL("create table mytable ("
                    + "id integer primary key autoincrement,"
                    + "date numeric,"
                    + "temperature1 real,"
                    + "signal1 real,"
                    + "temperature2 real,"
                    + "signal2 real" + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }

    }

    public class MyTimerTask extends TimerTask {

        private static final String LOG_TAG = "TimerTask DB";


        @Override
        public void run() {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            Log.d(LOG_TAG, "--- Insert in mytable: ---");
            // подготовим данные для вставки в виде пар: наименование столбца - значение
            ContentValues cv = new ContentValues();

            cv.put("date", new SimpleDateFormat("dd:MM:yyyy HH:mm").format(Calendar.getInstance().getTime()));
            cv.put("temperature1", DeviceControlActivity.get_temperature1);
            cv.put("signal1", DeviceControlActivity.get_signal1);
            cv.put("temperature2", DeviceControlActivity.get_temperature2);
            cv.put("signal2", DeviceControlActivity.get_signal2);
    
            // вставляем запись и получаем ее ID
            long rowID = db.insert("mytable", null, cv);
            Log.d(LOG_TAG, "row inserted, ID = " + rowID + " " + cv);

        }

    }


}



