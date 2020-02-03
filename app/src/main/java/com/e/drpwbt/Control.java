package com.e.drpwbt;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.e.drwbt.R;

import java.io.IOException;
import java.util.UUID;

public class Control extends Activity implements SensorEventListener {
    private static final String TAG = "DRPWBT";
    private UUID deviceUuid;
    private BluetoothSocket bluetoothSocket;

    private boolean isUserInitiatedDisconnect = false;
    private boolean isBluetoothConnected      = false;

    private BluetoothDevice device;

    final static String forwardString  = "F";
    final static String backwardString = "B";
    final static String leftString     = "L";
    final static String rightString    = "R";
    final static String stopString     = "S";

    private ProgressDialog progressDialog;
    Button forward, backward, left, right, stop;
    CheckBox useSensors;

    private SensorManager sensorManager;
    private long          lastUpdate;
    private boolean       areSensorsInUse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        forward    = findViewById(R.id.forward);
        backward   = findViewById(R.id.backward);
        left       = findViewById(R.id.left);
        right      = findViewById(R.id.right);
        stop       = findViewById(R.id.stop);
        useSensors = findViewById(R.id.use_sensors);

        Intent intent = getIntent();
        Bundle b = intent.getExtras();

        device     = b.getParcelable(DevicesList.DEVICE);
        deviceUuid = UUID.fromString(b.getString(DevicesList.DEVICE_UUID));

        Log.d(TAG, "Ready");

        useSensors.setOnClickListener(new View.OnClickListener()
        {
           @Override
           public void onClick(View v) {
               areSensorsInUse = useSensors.isChecked();
               forward.setEnabled (!areSensorsInUse);
               backward.setEnabled(!areSensorsInUse);
               left.setEnabled    (!areSensorsInUse);
               right.setEnabled   (!areSensorsInUse);
               stop.setEnabled    (!areSensorsInUse);
           }
        }
        );

        forward.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                try {
                    bluetoothSocket.getOutputStream().write(forwardString.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        );

        backward.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                try {
                    bluetoothSocket.getOutputStream().write(backwardString.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        );

        left.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
             try {
                    bluetoothSocket.getOutputStream().write(leftString.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        );

        right.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                try {
                    bluetoothSocket.getOutputStream().write(rightString.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        );

        stop.setOnClickListener(new View.OnClickListener()
         {
             @Override
             public void onClick(View v) {
                  try {
                     bluetoothSocket.getOutputStream().write(stopString.getBytes());
                 } catch (IOException e) {
                     e.printStackTrace();
                 }
             }
         }
        );

        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        lastUpdate    = System.currentTimeMillis();

        Sensor rotationSensor =
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (rotationSensor == null) {
            msg("Rotation vector not available");
            finish();
        }

        return;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (areSensorsInUse == true) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                getRotation(event);
            }
        }
        return;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (areSensorsInUse == true) {
        }
        return;
    }

    private void getRotation(SensorEvent event) {

        long actualTime = event.timestamp;
        byte outputBuffer[] = new byte[3];

        if (actualTime - lastUpdate < 100) {
            return;
        }
        lastUpdate = actualTime;

        float[] rotationMatrix = new float[16];
        SensorManager.getRotationMatrixFromVector(
                rotationMatrix,
                event.values);

        float[] orientations = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientations);

        for(int i = 0; i < 3; i++) {
            orientations[i] = (float)(Math.toDegrees(orientations[i]));
        }

        if (bluetoothSocket != null && isBluetoothConnected) {
            try {
                outputBuffer[0] = (byte)orientations[0];
                outputBuffer[1] = (byte)orientations[1];
                outputBuffer[2] = (byte)orientations[2];
                bluetoothSocket.getOutputStream().write(outputBuffer);
           } catch (IOException e) {
                e.printStackTrace();
           }
        }
    }

    private class DisConnectBT extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(Void... params) {

            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            isBluetoothConnected = false;
            if (isUserInitiatedDisconnect) {
                finish();
            }
        }
    }

    private void msg(final String str) {

        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onPause() {
        if (bluetoothSocket != null && isBluetoothConnected) {
            new DisConnectBT().execute();
        }
        Log.d(TAG, "Paused");
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        if (bluetoothSocket == null || !isBluetoothConnected) {
            new ConnectBT().execute();
        }
        Log.d(TAG, "Resumed");
        super.onResume();
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "Stopped");
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean isConnectSuccessful = true;

        @Override
        protected void onPreExecute() {

            progressDialog = ProgressDialog.show(Control.this, "Hold on", "Connecting...");
        }

        @Override
        protected Void doInBackground(Void... devices) {

            try {
                if (bluetoothSocket == null || !isBluetoothConnected) {
                    bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(deviceUuid);
                    bluetoothSocket.connect();
                }
            } catch (IOException e) {
                msg("Could not connect to BT device.\nPlease check your device or BT server");
                isConnectSuccessful = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (!isConnectSuccessful) {
                msg("Could not connect to BT device.\nTurn your device ON?...");
                finish();
            } else {
                msg("Connected to BT device");
                isBluetoothConnected = true;
            }

            progressDialog.dismiss();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}