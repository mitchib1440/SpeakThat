package com.micoyc.speakthat.settings.managers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import com.micoyc.speakthat.ProximityCover;
import com.micoyc.speakthat.gesture.WaveEvaluator;

public class WaveSensorManager implements SensorEventListener {
    public interface Listener {
        void onValidWave(int currentCount, int targetCount);
        void onTargetReached();
        void onWindowExpired();
        void onHoldScheduled(long holdDurationMs);
        void onHoldCancelled();
    }

    private final SensorManager sensorManager;
    private final Sensor proximitySensor;
    private final Listener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final WaveEvaluator evaluator;

    private boolean isTesting = false;
    private int targetCount = 1;

    public WaveSensorManager(Context context, Listener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        this.listener = listener;
        this.evaluator = new WaveEvaluator(1, 150L, false);
    }

    public void setTargetCount(int count) {
        this.targetCount = count;
        evaluator.setTargetCount(count);
    }

    public void setWaveHoldDurationMs(long durationMs) {
        evaluator.setWaveHoldDurationMs(durationMs);
    }

    public boolean start() {
        if (proximitySensor == null) {
            return false;
        }
        isTesting = true;
        evaluator.reset();
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

    public boolean isTesting() {
        return isTesting;
    }

    public boolean isAvailable() {
        return proximitySensor != null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_PROXIMITY || !isTesting) {
            return;
        }

        float proximityValue = event.values[0];
        boolean isNear = ProximityCover.isCovered(proximityValue, proximitySensor);

        WaveEvaluator.EvaluationResult result = evaluator.evaluate(isNear, true, System.currentTimeMillis());

        if (listener != null) {
            uiHandler.post(() -> {
                if (result instanceof WaveEvaluator.EvaluationResult.ValidWave) {
                    WaveEvaluator.EvaluationResult.ValidWave validWave = (WaveEvaluator.EvaluationResult.ValidWave) result;
                    listener.onValidWave(validWave.getCurrentCount(), targetCount);
                } else if (result instanceof WaveEvaluator.EvaluationResult.TargetReached) {
                    listener.onTargetReached();
                } else if (result instanceof WaveEvaluator.EvaluationResult.WindowExpired) {
                    listener.onWindowExpired();
                } else if (result instanceof WaveEvaluator.EvaluationResult.HoldScheduled) {
                    WaveEvaluator.EvaluationResult.HoldScheduled scheduled = (WaveEvaluator.EvaluationResult.HoldScheduled) result;
                    listener.onHoldScheduled(scheduled.getHoldDurationMs());
                } else if (result instanceof WaveEvaluator.EvaluationResult.HoldCancelled) {
                    listener.onHoldCancelled();
                }
            });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
