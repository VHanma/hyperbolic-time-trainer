package com.vhanma.freqsight;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import java.util.Locale;

final class SensorHub implements SensorEventListener {
    interface Listener { void onSensorSnapshot(DataModels.SensorSnapshot snapshot); }

    private final SensorManager manager;
    private final Listener listener;
    private final Object lock = new Object();
    private final float[] mag = new float[3];
    private final float[] accel = new float[3];
    private final float[] gyro = new float[3];
    private float light = Float.NaN;
    private float gravity = 9.81f;
    private float vibrationEma;
    private long lastDispatch;

    private boolean baselineReady;
    private int baselineCount;
    private double magMean, magM2, lightMean, lightM2, vibrationMean, vibrationM2;

    SensorHub(Context context, Listener listener) {
        this.manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;
    }

    void start() {
        register(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_GAME);
        register(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME);
        register(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_GAME);
        register(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void stop() { manager.unregisterListener(this); }

    private void register(int type, int delay) {
        Sensor s = manager.getDefaultSensor(type);
        if (s != null) manager.registerListener(this, s, delay);
    }

    void beginBaseline() {
        synchronized (lock) {
            baselineReady = false;
            baselineCount = 0;
            magMean = magM2 = lightMean = lightM2 = vibrationMean = vibrationM2 = 0;
        }
    }

    void completeBaseline() { synchronized (lock) { baselineReady = baselineCount >= 20; } }

    boolean isStable() {
        synchronized (lock) {
            float g = magnitude(gyro);
            float a = Math.abs(magnitude(accel) - gravity);
            return g < 0.055f && a < 0.20f && vibrationEma < 0.11f;
        }
    }

    DataModels.SensorSnapshot snapshot() {
        synchronized (lock) {
            DataModels.SensorSnapshot s = new DataModels.SensorSnapshot();
            s.timestampMs = System.currentTimeMillis();
            s.magX = mag[0]; s.magY = mag[1]; s.magZ = mag[2]; s.magMagnitude = magnitude(mag);
            s.accelX = accel[0]; s.accelY = accel[1]; s.accelZ = accel[2]; s.accelMagnitude = magnitude(accel);
            s.gyroX = gyro[0]; s.gyroY = gyro[1]; s.gyroZ = gyro[2]; s.gyroMagnitude = magnitude(gyro);
            s.lightLux = Float.isNaN(light) ? -1f : light;
            s.vibrationScore = vibrationEma;
            s.stable = isStable();
            StringBuilder anomalies = new StringBuilder();
            if (baselineReady) {
                float magZ = zScore(s.magMagnitude, magMean, magM2, baselineCount);
                float lightZ = Float.isNaN(light) ? 0 : zScore(light, lightMean, lightM2, baselineCount);
                float vibrationZ = zScore(vibrationEma, vibrationMean, vibrationM2, baselineCount);
                if (magZ > 3f) append(anomalies, String.format(Locale.US, "magnetometer %.1fσ", magZ));
                if (lightZ > 3f) append(anomalies, String.format(Locale.US, "light %.1fσ", lightZ));
                if (vibrationZ > 3f) append(anomalies, String.format(Locale.US, "vibration %.1fσ", vibrationZ));
            }
            s.anomalySummary = anomalies.length() == 0 ? "none" : anomalies.toString();
            return s;
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        synchronized (lock) {
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) copy3(event.values, mag);
            else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                copy3(event.values, accel);
                float m = magnitude(accel);
                gravity = gravity * 0.995f + m * 0.005f;
                float jerk = Math.abs(m - gravity);
                vibrationEma = vibrationEma * 0.88f + jerk * 0.12f;
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) copy3(event.values, gyro);
            else if (event.sensor.getType() == Sensor.TYPE_LIGHT) light = event.values[0];

            if (!baselineReady && baselineCount < 500) {
                baselineCount++;
                update(magnitude(mag), true);
                if (!Float.isNaN(light)) update(light, false);
                updateVibration(vibrationEma);
            }
        }
        long now = SystemClock.elapsedRealtime();
        if (listener != null && now - lastDispatch > 200L) {
            lastDispatch = now;
            listener.onSensorSnapshot(snapshot());
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void update(float value, boolean magnetic) {
        if (magnetic) {
            double d = value - magMean;
            magMean += d / baselineCount;
            magM2 += d * (value - magMean);
        } else {
            double d = value - lightMean;
            lightMean += d / baselineCount;
            lightM2 += d * (value - lightMean);
        }
    }

    private void updateVibration(float value) {
        double d = value - vibrationMean;
        vibrationMean += d / baselineCount;
        vibrationM2 += d * (value - vibrationMean);
    }

    private static float zScore(double value, double mean, double m2, int n) {
        if (n < 2) return 0f;
        double sd = Math.sqrt(Math.max(1e-6, m2 / (n - 1)));
        return (float) (Math.abs(value - mean) / sd);
    }

    private static void append(StringBuilder b, String text) {
        if (b.length() > 0) b.append(", ");
        b.append(text);
    }

    private static void copy3(float[] src, float[] dst) {
        for (int i = 0; i < 3 && i < src.length; i++) dst[i] = src[i];
    }

    private static float magnitude(float[] v) {
        return (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
    }
}
