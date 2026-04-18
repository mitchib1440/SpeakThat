package com.micoyc.speakthat.settings.managers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import com.micoyc.speakthat.gesture.ShakeEvaluator;

public class ShakeSensorManager implements SensorEventListener {
    public interface Listener {
        void onShakeValue(float current, float max);
        void onValidShake(int currentCount, int targetCount);
        void onTargetReached();
        void onWindowExpired();
    }

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Listener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ShakeEvaluator evaluator;

    private boolean isTesting = false;
    private float maxShakeValue = 0f;
    private int targetCount = 1;

    public ShakeSensorManager(Context context, Listener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.listener = listener;
        this.evaluator = new ShakeEvaluator(1, 12.0f);
    }

    public void setThreshold(float threshold) {
        evaluator.setThreshold(threshold);
    }

    public void setTargetCount(int count) {
        this.targetCount = count;
        evaluator.setTargetCount(count);
    }

    public boolean start() {
        if (accelerometer == null) {
            return false;
        }
        isTesting = true;
        maxShakeValue = 0f;
        evaluator.reset();
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

        ShakeEvaluator.EvaluationResult result = evaluator.evaluate(x, y, z);
        float currentShakeValue = result.getShakeValue();

        if (currentShakeValue > maxShakeValue) {
            maxShakeValue = currentShakeValue;
        }

        if (listener != null) {
            uiHandler.post(() -> {
                listener.onShakeValue(currentShakeValue, maxShakeValue);
                if (result instanceof ShakeEvaluator.EvaluationResult.ValidShake) {
                    ShakeEvaluator.EvaluationResult.ValidShake validShake = (ShakeEvaluator.EvaluationResult.ValidShake) result;
                    listener.onValidShake(validShake.getCurrentCount(), targetCount);
                } else if (result instanceof ShakeEvaluator.EvaluationResult.TargetReached) {
                    listener.onTargetReached();
                } else if (result instanceof ShakeEvaluator.EvaluationResult.WindowExpired) {
                    listener.onWindowExpired();
                }
            });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
