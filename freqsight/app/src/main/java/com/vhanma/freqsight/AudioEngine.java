package com.vhanma.freqsight;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import androidx.core.content.ContextCompat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class AudioEngine {
    interface Listener { void onAudioSnapshot(DataModels.AudioSnapshot snapshot); }

    private static final int SAMPLE_RATE = 48000;
    private static final int FFT_SIZE = 4096;
    private final Context context;
    private final Listener listener;
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private Thread thread;
    private AudioRecord recorder;
    private volatile DataModels.AudioSnapshot latest = new DataModels.AudioSnapshot();
    private final Deque<Long> clickTimes = new ArrayDeque<>();

    private boolean baselineReady;
    private int baselineCount;
    private double rmsMean, rmsM2, peakMean, peakM2;

    AudioEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void beginBaseline() {
        baselineReady = false;
        baselineCount = 0;
        rmsMean = rmsM2 = peakMean = peakM2 = 0;
    }

    void completeBaseline() { baselineReady = baselineCount >= 8; }

    DataModels.AudioSnapshot snapshot() { return latest; }

    void startListening() {
        if (listening.get()) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(min, FFT_SIZE * 4);
        recorder = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release(); recorder = null; return;
        }
        listening.set(true);
        recorder.startRecording();
        thread = new Thread(this::listenLoop, "FreqSightAudio");
        thread.start();
    }

    void stopListening() {
        listening.set(false);
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
            recorder.release();
            recorder = null;
        }
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) {}
            thread = null;
        }
    }

    private void listenLoop() {
        short[] buffer = new short[FFT_SIZE];
        while (listening.get() && recorder != null) {
            int read = recorder.read(buffer, 0, buffer.length);
            if (read < FFT_SIZE / 2) continue;
            DataModels.AudioSnapshot s = analyze(buffer, read);
            latest = s;
            if (listener != null) listener.onAudioSnapshot(s);
        }
    }

    private DataModels.AudioSnapshot analyze(short[] samples, int n) {
        double sum = 0;
        double max = 0;
        double[] real = new double[FFT_SIZE];
        double[] imag = new double[FFT_SIZE];
        for (int i = 0; i < FFT_SIZE; i++) {
            double v = i < n ? samples[i] / 32768.0 : 0;
            sum += v * v;
            max = Math.max(max, Math.abs(v));
            double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1));
            real[i] = v * w;
        }
        fft(real, imag);
        int peakBin = 1;
        double peakMag = 0;
        for (int i = 1; i < FFT_SIZE / 2; i++) {
            double mag = real[i] * real[i] + imag[i] * imag[i];
            if (mag > peakMag) { peakMag = mag; peakBin = i; }
        }
        float rms = (float) Math.sqrt(sum / Math.max(1, n));
        float peakHz = peakBin * SAMPLE_RATE / (float) FFT_SIZE;
        float db = (float) (20.0 * Math.log10(Math.max(1e-8, Math.sqrt(peakMag) / FFT_SIZE)));
        boolean click = max > Math.max(0.45, rms * 8.0);
        long now = System.currentTimeMillis();
        if (click) {
            clickTimes.addLast(now);
            while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > 15000) clickTimes.removeFirst();
        }
        List<Long> intervals = new ArrayList<>();
        Long previous = null;
        for (Long t : clickTimes) {
            if (previous != null) intervals.add(t - previous);
            previous = t;
        }
        String pattern = intervalPattern(intervals);
        boolean repeated = !pattern.isEmpty();

        if (!baselineReady && baselineCount < 100) {
            baselineCount++;
            double d = rms - rmsMean; rmsMean += d / baselineCount; rmsM2 += d * (rms - rmsMean);
            d = peakHz - peakMean; peakMean += d / baselineCount; peakM2 += d * (peakHz - peakMean);
        }

        DataModels.AudioSnapshot s = new DataModels.AudioSnapshot();
        s.timestampMs = now;
        s.rms = rms;
        s.peakFrequencyHz = peakHz;
        s.peakDb = db;
        s.click = click;
        s.pulse = click || (baselineReady && audioZ(rms) > 3.0f);
        s.repeatedPattern = repeated;
        s.pattern = pattern;
        return s;
    }

    private float audioZ(float rms) {
        if (baselineCount < 2) return 0;
        double sd = Math.sqrt(Math.max(1e-9, rmsM2 / (baselineCount - 1)));
        return (float) (Math.abs(rms - rmsMean) / sd);
    }

    private String intervalPattern(List<Long> intervals) {
        if (intervals.size() < 3) return "";
        int n = intervals.size();
        long a = intervals.get(n - 1), b = intervals.get(n - 2), c = intervals.get(n - 3);
        long tol = 120;
        if (Math.abs(a - b) < tol && Math.abs(b - c) < tol) {
            return String.format(Locale.US, "three repeated pulses near %.2f s", a / 1000f);
        }
        if (n >= 5) {
            long[] last = new long[5];
            for (int i = 0; i < 5; i++) last[i] = intervals.get(n - 5 + i);
            if (last[1] >= last[0] && last[2] >= last[1] && last[3] >= last[2] && last[4] >= last[3]) {
                return "ascending pulse intervals";
            }
        }
        return "";
    }

    int deviceOutputSampleRate() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        try { return Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)); }
        catch (Exception e) { return SAMPLE_RATE; }
    }

    boolean supportsUltrasonicStyle() { return deviceOutputSampleRate() >= 48000; }

    void playTone(double frequency, int durationMs, float amplitude, boolean left, boolean right) {
        amplitude = Math.max(0f, Math.min(0.12f, amplitude));
        int sampleRate = Math.max(44100, deviceOutputSampleRate());
        int count = Math.max(1, sampleRate * durationMs / 1000);
        short[] data = new short[count * 2];
        for (int i = 0; i < count; i++) {
            double envelope = Math.min(1.0, i / (sampleRate * 0.015)) * Math.min(1.0, (count - i) / (sampleRate * 0.02));
            short v = (short) (Math.sin(2 * Math.PI * frequency * i / sampleRate) * amplitude * envelope * Short.MAX_VALUE);
            data[i * 2] = left ? v : 0;
            data[i * 2 + 1] = right ? v : 0;
        }
        new Thread(() -> {
            try {
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                        data.length * 2, AudioTrack.MODE_STATIC);
                track.write(data, 0, data.length);
                track.play();
                Thread.sleep(durationMs + 60L);
                track.stop(); track.release();
            } catch (Exception ignored) {}
        }, "FreqSightTone").start();
    }

    void playSweep(double fromHz, double toHz, int durationMs, float amplitude) {
        amplitude = Math.max(0f, Math.min(0.10f, amplitude));
        int sampleRate = Math.max(44100, deviceOutputSampleRate());
        int count = Math.max(1, sampleRate * durationMs / 1000);
        short[] data = new short[count * 2];
        double phase = 0;
        for (int i = 0; i < count; i++) {
            double f = fromHz + (toHz - fromHz) * i / Math.max(1.0, count - 1.0);
            phase += 2 * Math.PI * f / sampleRate;
            double env = Math.sin(Math.PI * i / Math.max(1.0, count - 1.0));
            short v = (short) (Math.sin(phase) * amplitude * env * Short.MAX_VALUE);
            data[i * 2] = v; data[i * 2 + 1] = v;
        }
        new Thread(() -> {
            try {
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                        data.length * 2, AudioTrack.MODE_STATIC);
                track.write(data, 0, data.length); track.play();
                Thread.sleep(durationMs + 60L); track.stop(); track.release();
            } catch (Exception ignored) {}
        }, "FreqSightSweep").start();
    }

    private static void fft(double[] real, double[] imag) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tr = real[i]; real[i] = real[j]; real[j] = tr;
                double ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wlenCos = Math.cos(ang), wlenSin = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double wCos = 1, wSin = 0;
                for (int j = 0; j < len / 2; j++) {
                    int u = i + j, v = i + j + len / 2;
                    double vr = real[v] * wCos - imag[v] * wSin;
                    double vi = real[v] * wSin + imag[v] * wCos;
                    real[v] = real[u] - vr; imag[v] = imag[u] - vi;
                    real[u] += vr; imag[u] += vi;
                    double nextCos = wCos * wlenCos - wSin * wlenSin;
                    wSin = wCos * wlenSin + wSin * wlenCos;
                    wCos = nextCos;
                }
            }
        }
    }
}
