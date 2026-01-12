package com.micoyc.speakthat.settings.managers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

public class WaveSensorManager implements SensorEventListener {
    public interface Listener {
        void onWaveValue(float distance, float minDistance);
    }

    private final SensorManager sensorManager;
    private final Sensor proximitySensor;
    private final Listener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private boolean isTesting = false;
    private float minWaveValue = 5.0f;

    public WaveSensorManager(Context context, Listener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        this.listener = listener;
    }

    public boolean start() {
        if (proximitySensor == null) {
            return false;
        }
        isTesting = true;
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI);
        return true;
    }

    public void stop() {
        if (!isTesting) {
            return;
        }
        isTesting = false;
        sensorManager.unregisterListener(this);
    }

    public void resetMinDistance(float initialValue) {
        minWaveValue = initialValue;
    }

    public boolean isTesting() {
        return isTesting;
    }

    public boolean isAvailable() {
        return proximitySensor != null;
    }

    public float getMinWaveValue() {
        return minWaveValue;
    }

    public float getSensorMaximumRange() {
        return proximitySensor != null ? proximitySensor.getMaximumRange() : -1f;
    }

    public String getSensorName() {
        return proximitySensor != null ? proximitySensor.getName() : null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_PROXIMITY || !isTesting) {
            return;
        }

        float currentWaveValue = event.values[0];
        if (currentWaveValue < minWaveValue) {
            minWaveValue = currentWaveValue;
        }

        if (listener != null) {
            uiHandler.post(() -> listener.onWaveValue(currentWaveValue, minWaveValue));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
