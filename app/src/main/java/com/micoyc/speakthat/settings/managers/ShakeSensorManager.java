/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.settings.managers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

public class ShakeSensorManager implements SensorEventListener {
    public interface Listener {
        void onShakeValue(float current, float max);
    }

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Listener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private boolean isTesting = false;
    private float maxShakeValue = 0f;

    public ShakeSensorManager(Context context, Listener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.listener = listener;
    }

    public boolean start() {
        if (accelerometer == null) {
            return false;
        }
        isTesting = true;
        maxShakeValue = 0f;
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        return true;
    }

    public void stop() {
        if (!isTesting) {
            return;
        }
        isTesting = false;
        sensorManager.unregisterListener(this);
    }

    public boolean isTesting() {
        return isTesting;
    }

    public boolean isAvailable() {
        return accelerometer != null;
    }

    public float getMaxShakeValue() {
        return maxShakeValue;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || !isTesting) {
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float currentShakeValue = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
        if (currentShakeValue > maxShakeValue) {
            maxShakeValue = currentShakeValue;
        }

        if (listener != null) {
            uiHandler.post(() -> listener.onShakeValue(currentShakeValue, maxShakeValue));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
