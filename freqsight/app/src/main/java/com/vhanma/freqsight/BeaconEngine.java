package com.vhanma.freqsight;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BeaconEngine {
    interface Listener {
        void onBeaconState(String state);
        void onBeaconPulse(BeaconPulse pulse);
        void onVisualPulse(boolean on);
    }

    static final class BeaconPulse {
        long timestampMs;
        String pattern;
        double frequencyHz;
        int durationMs;
        boolean lightOn;
        boolean audible;
        String label;

        String line() {
            return String.format(Locale.US, "%d,%s,%.1fHz,%dms,light=%s,audible=%s,%s",
                    timestampMs, pattern, frequencyHz, durationMs, lightOn, audible, label);
        }
    }

    enum Pattern {
        PRIME_CALL("Prime Call"),
        FIBONACCI_CALL("Fibonacci Call"),
        SKYWATCHER_SWEEP("Skywatcher Sweep"),
        SILENT_CARRIER("Silent Carrier Simulation"),
        SCALAR_INSPIRED("Scalar-Inspired Standing Wave Simulation"),
        MIRROR_CALL("Mirror Call");
        final String label;
        Pattern(String label) { this.label = label; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioEngine audio;
    private final Listener listener;
    private final List<Long> emittedTimes = new ArrayList<>();
    private boolean running;
    private int generation;

    BeaconEngine(AudioEngine audio, Listener listener) {
        this.audio = audio;
        this.listener = listener;
    }

    boolean isRunning() { return running; }
    List<Long> emittedTimes() { return new ArrayList<>(emittedTimes); }

    void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (listener != null) {
            listener.onVisualPulse(false);
            listener.onBeaconState("Skywatcher Beacon stopped. Passive listening restored.");
        }
    }

    void start(Pattern pattern, double baseHz, boolean audioOn, boolean visualOnly, int unitMs) {
        stop();
        running = true;
        generation++;
        final int g = generation;
        emittedTimes.clear();
        if (listener != null) listener.onBeaconState("Skywatcher Beacon armed. " + pattern.label);
        switch (pattern) {
            case PRIME_CALL -> sequence(pattern, new int[]{2,3,5,7,11}, baseHz, audioOn && !visualOnly, unitMs, g);
            case FIBONACCI_CALL -> sequence(pattern, new int[]{1,1,2,3,5,8}, baseHz, audioOn && !visualOnly, unitMs, g);
            case SKYWATCHER_SWEEP -> sweep(pattern, Math.max(1000, baseHz * 0.65), Math.min(19500, baseHz * 1.35), audioOn && !visualOnly, g);
            case SILENT_CARRIER -> sequence(pattern, new int[]{1,2,3,5,8}, baseHz, false, unitMs, g);
            case SCALAR_INSPIRED -> scalar(pattern, baseHz, audioOn && !visualOnly, unitMs, g);
            case MIRROR_CALL -> sequence(pattern, new int[]{2,3,2,5,3}, baseHz, audioOn && !visualOnly, unitMs, g);
        }
    }

    private void sequence(Pattern pattern, int[] spacing, double hz, boolean audible, int unitMs, int g) {
        long delay = 0;
        for (int i = 0; i < spacing.length; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                if (!valid(g)) return;
                pulse(pattern, hz + index * 37.0, 180, audible, "pulse " + (index + 1));
            }, delay);
            delay += Math.max(250, spacing[i] * unitMs);
        }
        finishLater(pattern, delay + 500, g);
    }

    private void sweep(Pattern pattern, double from, double to, boolean audible, int g) {
        handler.post(() -> {
            if (!valid(g)) return;
            visual(true);
            if (audible) audio.playSweep(from, to, 5000, 0.08f);
            BeaconPulse p = createPulse(pattern, from, 5000, true, audible,
                    String.format(Locale.US, "sweep %.0f to %.0f Hz", from, to));
            dispatch(p);
        });
        handler.postDelayed(() -> { if (valid(g)) visual(false); }, 5000);
        finishLater(pattern, 5600, g);
    }

    private void scalar(Pattern pattern, double base, boolean audible, int unitMs, int g) {
        int[] intervals = {1,2,1,3,2,5};
        long delay = 0;
        for (int i = 0; i < intervals.length; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                if (!valid(g)) return;
                double hz = Math.max(200, base + (index % 2 == 0 ? -73 : 73));
                visual(true);
                if (audible) {
                    audio.playTone(hz, 260, 0.06f, index % 2 == 0, index % 2 != 0);
                }
                BeaconPulse p = createPulse(pattern, hz, 260, true, audible,
                        "fictional opposing-channel timing marker");
                dispatch(p);
                handler.postDelayed(() -> { if (valid(g)) visual(false); }, 260);
            }, delay);
            delay += Math.max(280, intervals[i] * unitMs);
        }
        finishLater(pattern, delay + 500, g);
    }

    private void pulse(Pattern pattern, double hz, int duration, boolean audible, String label) {
        visual(true);
        if (audible) audio.playTone(hz, duration, 0.08f, true, true);
        BeaconPulse p = createPulse(pattern, hz, duration, true, audible, label);
        dispatch(p);
        handler.postDelayed(() -> visual(false), duration);
    }

    private BeaconPulse createPulse(Pattern pattern, double hz, int duration, boolean light, boolean audible, String label) {
        BeaconPulse p = new BeaconPulse();
        p.timestampMs = System.currentTimeMillis();
        p.pattern = pattern.label;
        p.frequencyHz = hz;
        p.durationMs = duration;
        p.lightOn = light;
        p.audible = audible;
        p.label = label;
        return p;
    }

    private void dispatch(BeaconPulse p) {
        emittedTimes.add(p.timestampMs);
        if (listener != null) listener.onBeaconPulse(p);
    }

    private void visual(boolean on) {
        if (listener != null) listener.onVisualPulse(on);
    }

    private void finishLater(Pattern pattern, long delay, int g) {
        handler.postDelayed(() -> {
            if (!valid(g)) return;
            running = false;
            visual(false);
            if (listener != null) {
                listener.onBeaconState(pattern.label + " emitted. Listening for environmental response.");
            }
        }, delay);
    }

    private boolean valid(int g) { return running && generation == g; }
}
