package ru.ipmavlutov.metallsensor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import ru.ipmavlutov.metallsensor.Activity.DeviceControlActivity;

public class DeviceConnector {
    private static final String TAG = "DeviceConnector";
    private static final boolean D = false;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTING = 1; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 2;  // now connected to a remote device


    private int mState;

    private final BluetoothAdapter btAdapter;
    private final BluetoothDevice connectedDevice;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private final Handler mHandler;
    private final String deviceName;

    // ==========================================================================
    public DeviceConnector(DeviceData deviceData, Handler handler) {
        mHandler = handler;
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        connectedDevice = btAdapter.getRemoteDevice(deviceData.getAddress());
        deviceName = (deviceData.getName() == null) ? deviceData.getAddress() : deviceData.getName();
        mState = STATE_NONE;
    }


    /**
     * Запрос на соединение с устойством
     */
    public synchronized void connect() throws IOException {
        if (D) Log.d(TAG, "connect to: " + connectedDevice);

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                if (D) Log.d(TAG, "cancel mConnectThread");
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mConnectedThread != null) {
            if (D) Log.d(TAG, "cancel mConnectedThread");
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(connectedDevice);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }
    // ==========================================================================

    /**
     * Завершение соединения
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            if (D) Log.d(TAG, "cancel mConnectThread");
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            if (D) Log.d(TAG, "cancel mConnectedThread");
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }
    // ==========================================================================


    /**
     * Установка внутреннего состояния устройства
     *
     * @param state - состояние
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
        mHandler.obtainMessage(DeviceControlActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }
    // ==========================================================================


    /**
     * Получение состояния устройства
     */
    public synchronized int getState() {
        return mState;
    }
    // ==========================================================================


    public synchronized void connected(BluetoothSocket socket) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            if (D) Log.d(TAG, "cancel mConnectThread");
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            if (D) Log.d(TAG, "cancel mConnectedThread");
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_CONNECTED);

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(DeviceControlActivity.MESSAGE_DEVICE_NAME, deviceName);
        mHandler.sendMessage(msg);

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

    }
    // ==========================================================================


    /*public void write(byte[] data) {
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }

        // Perform the write unsynchronized
        if (data.length == 1) r.write(data[0]);
        else r.writeData(data);
    }*/
    // ==========================================================================


    private void connectionFailed() {
        if (D) Log.d(TAG, "connectionFailed");

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(DeviceControlActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(STATE_NONE);
    }
    // ==========================================================================


    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(DeviceControlActivity.MESSAGE_TOAST, deviceName);
        Bundle bundle = new Bundle();
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(STATE_NONE);


    }
    // ==========================================================================


    /**
     * Класс потока для соединения с BT-устройством
     */
    // ==========================================================================
    private class ConnectThread extends Thread {
        private static final String TAG = "ConnectThread";
        private static final boolean D = false;

        private final BluetoothSocket mmSocket;
        private final UUID uuids = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

        public ConnectThread(BluetoothDevice device) throws IOException {
            if (D) Log.d(TAG, "create ConnectThread");
            mmSocket = device.createRfcommSocketToServiceRecord(uuids);
        }
        // ==========================================================================

        /**
         * Основной рабочий метод для соединения с устройством.
         * При успешном соединении передаёт управление другому потоку
         */
        public void run() {
            if (D) Log.d(TAG, "ConnectThread run");
            btAdapter.cancelDiscovery();
            if (mmSocket == null) {
                if (D) Log.d(TAG, "unable to connect to device, socket isn't created");
                connectionFailed();
                return;
            }

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    if (D) Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (DeviceConnector.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket);
        }
        // ==========================================================================


        /**
         * Отмена соединения
         */
        public void cancel() {
            if (D) Log.d(TAG, "ConnectThread cancel");

            if (mmSocket == null) {
                if (D) Log.d(TAG, "unable to close null socket");
                return;
            }
            try {
                mmSocket.close();
            } catch (IOException e) {
                if (D) Log.e(TAG, "close() of connect socket failed", e);
            }
        }

        // ==========================================================================
    }
    // ==========================================================================


    /**
     * Класс потока для обмена данными с BT-устройством
     */
    // ==========================================================================
    private class ConnectedThread extends Thread {
        private static final String TAG = "ConnectedThread";
        private static final boolean D = false;

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
       // private final OutputStream mmOutStream;


        public ConnectedThread(BluetoothSocket socket) {
            if (D) Log.d(TAG, "create ConnectedThread");

            mmSocket = socket;
            InputStream tmpIn = null;

            //OutputStream tmpOut = null;


            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                //tmpOut = socket.getOutputStream();

            } catch (IOException e) {
                if (D) Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
           // mmOutStream = tmpOut;


        }
        // ==========================================================================

        /**
         * Основной рабочий метод - ждёт входящих команд от потока
         */
        public void run() {
            if (D) Log.i(TAG, "ConnectedThread run");


            byte[] buffer = new byte[4096];
            String data = "";
            try {
                //noinspection InfiniteLoopStatement
                while (true) {
                    int n = mmInStream.read(buffer);
                    Log.d(TAG, "n=" + n);
                    data = String.valueOf(data) + new String(buffer, "ISO-8859-1").substring(0, n);
                    data = (data.replaceAll("\r\n", "\r").replaceAll("\n", "\r"));
                    Log.d(TAG, data);
                    if (!data.contains("\r") && data.length() <= 4096) continue;
                    if (data.contains("\r")) {
                        mHandler.obtainMessage(DeviceControlActivity.SUPERSIGNAL, data.lastIndexOf("\r"), -1, data.substring(0, 1 + data.lastIndexOf("\r")).getBytes("ISO-8859-1")).sendToTarget();
                        data = (data.substring(1 + data.lastIndexOf("\r")));
                        continue;
                    }
                    if (data.length() <= 4096) continue;
                    mHandler.obtainMessage(DeviceControlActivity.SUPERSIGNAL, data.length(), -1, data.getBytes("ISO-8859-1")).sendToTarget();
                    data = ("");

                }
            } catch (IOException e) {
                e.printStackTrace();
                connectionLost();
            }
           /* s = String.valueOf(Arrays.toString(buffer));
            reversebyte = buffer;
            Log.d(TAG, "buff" + s);
            ArrayUtils.reverse(reversebyte);
            c = String.valueOf(Arrays.toString(reversebyte));
            // Log.d(TAG, "reverse=" + c);
            for (
                    int i = 0;
                    i < reversebyte.length - 1; ++i)

            {
                if (reversebyte[i] == 10 && reversebyte[i + 1] == 13) {//&& reversebyte[i+13]==10&&reversebyte[i+14]==13) {
                    tmpbuf = Arrays.copyOfRange(reversebyte, i + 2, i + 14);
                    t = String.valueOf(Arrays.toString(tmpbuf));
                    ArrayUtils.reverse(tmpbuf);
                    //  Log.d(TAG, "tmp=" + t);

                    break;
                }
            }

            if ((buffer[9] == 10) && (buffer[8] == 13))

            {
                byte[] tmp = Arrays.copyOfRange(buffer, 0, 2);
                byte[] sgn = Arrays.copyOfRange(buffer, 2, 4);
                byte[] supersgn = Arrays.copyOfRange(buffer, 4, 6);

                mHandler.obtainMessage(DeviceControlActivity.TEMPRETURE, 0, -1, tmp).sendToTarget();
                mHandler.obtainMessage(DeviceControlActivity.SIGNAL, 0, -1, sgn).sendToTarget();
                //mHandler.obtainMessage(DeviceControlActivity.SUPERSIGNAL, 0, -1, supersgn).sendToTarget();
            }*/

        }


// ==========================================================================


        /**
         * Записать кусок данных в устройство
         */

       /* public void writeData(byte[] chunk) {

            try {
                mmOutStream.write(chunk);
                mmOutStream.flush();
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(DeviceControlActivity.MESSAGE_WRITE, -1, -1, chunk).sendToTarget();
            } catch (IOException e) {
                if (D) Log.e(TAG, "Exception during write", e);
            }
        }*/
// ==========================================================================


        /**
         * Записать байт
         */
       /* public void write(byte command) {
            byte[] buffer = new byte[1];
            buffer[0] = command;

            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(DeviceControlActivity.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                if (D) Log.e(TAG, "Exception during write", e);
            }
        }*/
// ==========================================================================


        /**
         * Отмена - закрытие сокета
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                if (D) Log.e(TAG, "close() of connect socket failed", e);
            }
        }
        // ==========================================================================
    }

// ==========================================================================


}