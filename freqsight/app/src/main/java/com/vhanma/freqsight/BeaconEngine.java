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
        int cycle;
        int index;
        int strength;

        String line() {
            return String.format(Locale.US,
                    "%d,%s,%.1fHz,%dms,light=%s,audible=%s,cycle=%d,index=%d,strength=%d,%s",
                    timestampMs, pattern, frequencyHz, durationMs, lightOn, audible,
                    cycle, index, strength, label);
        }
    }

    enum Pattern {
        PRIME_CALL("Prime Call"),
        NESTED_PRIME("Nested Prime Ladder"),
        FIBONACCI_CALL("Fibonacci Call"),
        FIBONACCI_MIRROR("Fibonacci Mirror Ladder"),
        HARMONIC_LADDER("Harmonic Ladder"),
        SKYWATCHER_SWEEP("Skywatcher Sweep"),
        DUAL_SKY_SWEEP("Dual Sky Sweep"),
        TRIAD_HANDSHAKE("Triad Mathematical Handshake"),
        SILENT_CARRIER("Silent Carrier Simulation"),
        SCALAR_INSPIRED("Scalar-Inspired Standing Wave Simulation"),
        MIRROR_CALL("Mirror Call"),
        COMPLETION_PROBE("Sequence Completion Probe");
        final String label;
        Pattern(String label) { this.label = label; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioEngine audio;
    private final Listener listener;
    private final List<Long> emittedTimes = new ArrayList<>();
    private final List<Long> expectedIntervals = new ArrayList<>();
    private boolean running;
    private int generation;
    private long emissionEndMs;
    private long responseWindowEndMs;
    private int currentStrength = 1;

    BeaconEngine(AudioEngine audio, Listener listener) {
        this.audio = audio;
        this.listener = listener;
    }

    boolean isRunning() { return running; }
    List<Long> emittedTimes() { return new ArrayList<>(emittedTimes); }
    List<Long> expectedIntervals() { return new ArrayList<>(expectedIntervals); }
    long emissionEndMs() { return emissionEndMs; }
    boolean responseWindowOpen() {
        long now = System.currentTimeMillis();
        return !running && emissionEndMs > 0 && now >= emissionEndMs && now <= responseWindowEndMs;
    }

    void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        visual(false);
        if (listener != null) listener.onBeaconState("Skywatcher Beacon stopped. Passive listening restored.");
    }

    void start(Pattern pattern, double baseHz, boolean audioOn, boolean visualOnly, int unitMs) {
        start(pattern, baseHz, audioOn, visualOnly, unitMs, 2, 1, true, 60000);
    }

    void start(Pattern pattern, double baseHz, boolean audioOn, boolean visualOnly,
               int unitMs, int strength, int repeats, boolean stereoWeave, int responseWindowMs) {
        stop();
        running = true;
        generation++;
        final int g = generation;
        currentStrength = clamp(strength, 1, 4);
        repeats = clamp(repeats, 1, 5);
        unitMs = clamp(unitMs, 150, 1500);
        emittedTimes.clear();
        expectedIntervals.clear();
        emissionEndMs = 0;
        responseWindowEndMs = 0;
        if (listener != null) {
            listener.onBeaconState("Skywatcher Beacon armed. " + pattern.label +
                    " • density " + currentStrength + " • cycles " + repeats);
        }
        boolean audible = audioOn && !visualOnly;
        long total;
        switch (pattern) {
            case PRIME_CALL -> total = scheduleSequence(pattern, new int[]{2,3,5,7,11}, baseHz, audible, unitMs, repeats, stereoWeave, g, false);
            case NESTED_PRIME -> total = scheduleNestedPrime(pattern, baseHz, audible, unitMs, repeats, stereoWeave, g);
            case FIBONACCI_CALL -> total = scheduleSequence(pattern, new int[]{1,1,2,3,5,8}, baseHz, audible, unitMs, repeats, stereoWeave, g, false);
            case FIBONACCI_MIRROR -> total = scheduleSequence(pattern, new int[]{1,1,2,3,5,8,5,3,2,1,1}, baseHz, audible, unitMs, repeats, stereoWeave, g, true);
            case HARMONIC_LADDER -> total = scheduleHarmonicLadder(pattern, baseHz, audible, unitMs, repeats, stereoWeave, g);
            case SKYWATCHER_SWEEP -> total = scheduleSweep(pattern, baseHz, audible, repeats, false, g);
            case DUAL_SKY_SWEEP -> total = scheduleSweep(pattern, baseHz, audible, repeats, true, g);
            case TRIAD_HANDSHAKE -> total = scheduleTriad(pattern, baseHz, audible, unitMs, repeats, stereoWeave, g);
            case SILENT_CARRIER -> total = scheduleSequence(pattern, new int[]{1,2,3,5,8,13}, baseHz, false, unitMs, repeats, stereoWeave, g, false);
            case SCALAR_INSPIRED -> total = scheduleScalar(pattern, baseHz, audible, unitMs, repeats, g);
            case MIRROR_CALL -> total = scheduleSequence(pattern, new int[]{2,3,5,7,5,3,2}, baseHz, audible, unitMs, repeats, stereoWeave, g, true);
            case COMPLETION_PROBE -> total = scheduleCompletionProbe(pattern, baseHz, audible, unitMs, repeats, stereoWeave, g);
            default -> total = 1000;
        }
        finishLater(pattern, total + 450, responseWindowMs, g);
    }

    private long scheduleSequence(Pattern pattern, int[] spacing, double baseHz, boolean audible,
                                  int unitMs, int repeats, boolean stereoWeave, int g, boolean mirrored) {
        long delay = 0;
        for (int cycle = 0; cycle < repeats; cycle++) {
            for (int i = 0; i < spacing.length; i++) {
                final int c = cycle, index = i;
                long scheduled = delay;
                registerInterval(scheduled);
                handler.postDelayed(() -> {
                    if (!valid(g)) return;
                    double step = mirrored ? Math.abs((spacing.length - 1) / 2.0 - index) : index;
                    double hz = cap(baseHz + step * (29 + currentStrength * 11));
                    int duration = 130 + currentStrength * 35;
                    boolean left = !stereoWeave || ((index + c) % 2 == 0);
                    boolean right = !stereoWeave || !left;
                    emitTonePulse(pattern, hz, duration, audible, left, right, c + 1, index + 1,
                            "coded spacing " + spacing[index] + " units");
                }, scheduled);
                delay += Math.max(180, spacing[i] * unitMs);
            }
            delay += unitMs * 2L;
        }
        return delay;
    }

    private long scheduleNestedPrime(Pattern pattern, double baseHz, boolean audible, int unitMs,
                                     int repeats, boolean stereoWeave, int g) {
        int[][] layers = {{2,3,5,7,11}, {3,5,7,11,13}, {5,7,11,13,17}};
        long delay = 0;
        int layerCount = Math.min(layers.length, 1 + currentStrength);
        for (int cycle = 0; cycle < repeats; cycle++) {
            for (int layer = 0; layer < layerCount; layer++) {
                int[] seq = layers[layer];
                for (int i = 0; i < seq.length; i++) {
                    final int c = cycle, l = layer, index = i;
                    long scheduled = delay;
                    registerInterval(scheduled);
                    handler.postDelayed(() -> {
                        if (!valid(g)) return;
                        double fundamental = cap(baseHz + l * 211 + index * 31);
                        double[] chord = harmonicSet(fundamental, 2 + currentStrength);
                        boolean left = !stereoWeave || ((index + l) % 2 == 0);
                        boolean right = !stereoWeave || !left;
                        emitChordPulse(pattern, chord, 150 + currentStrength * 30, audible,
                                left, right, c + 1, index + 1,
                                "nested prime layer " + (l + 1) + " spacing " + seq[index]);
                    }, scheduled);
                    delay += Math.max(170, seq[i] * Math.max(120, unitMs / layerCount));
                }
                delay += unitMs;
            }
            delay += unitMs * 2L;
        }
        return delay;
    }

    private long scheduleHarmonicLadder(Pattern pattern, double baseHz, boolean audible, int unitMs,
                                        int repeats, boolean stereoWeave, int g) {
        long delay = 0;
        int steps = 5 + currentStrength * 2;
        for (int cycle = 0; cycle < repeats; cycle++) {
            for (int i = 0; i < steps; i++) {
                final int c = cycle, index = i;
                long scheduled = delay;
                registerInterval(scheduled);
                handler.postDelayed(() -> {
                    if (!valid(g)) return;
                    double fundamental = cap(baseHz * (1.0 + index * 0.075));
                    double[] chord = harmonicSet(fundamental, 1 + currentStrength);
                    boolean left = !stereoWeave || ((index + c) % 2 == 0);
                    boolean right = !stereoWeave || !left;
                    emitChordPulse(pattern, chord, 180 + currentStrength * 30, audible,
                            left, right, c + 1, index + 1,
                            "harmonic rung " + (index + 1));
                }, scheduled);
                delay += unitMs + (i % 3) * 73L;
            }
            delay += unitMs * 2L;
        }
        return delay;
    }

    private long scheduleSweep(Pattern pattern, double baseHz, boolean audible, int repeats,
                               boolean dual, int g) {
        long delay = 0;
        int duration = 3000 + currentStrength * 1000;
        for (int cycle = 0; cycle < repeats; cycle++) {
            final int c = cycle;
            long scheduled = delay;
            registerInterval(scheduled);
            handler.postDelayed(() -> {
                if (!valid(g)) return;
                double from = Math.max(800, baseHz * 0.58);
                double to = Math.min(19500, baseHz * 1.42);
                visual(true);
                if (audible) {
                    if (dual) audio.playDualSweep(from, to, to, from, duration, amplitude(), true);
                    else audio.playSweep(from, to, duration, amplitude());
                }
                BeaconPulse p = createPulse(pattern, from, duration, true, audible,
                        dual ? "opposing stereo sweep" : "rising sky sweep", c + 1, 1);
                dispatch(p);
                handler.postDelayed(() -> { if (valid(g)) visual(false); }, duration);
            }, scheduled);
            delay += duration + 1200L;
        }
        return delay;
    }

    private long scheduleTriad(Pattern pattern, double baseHz, boolean audible, int unitMs,
                               int repeats, boolean stereoWeave, int g) {
        int[] spacing = {2,3,5,2,3,5,7};
        long delay = 0;
        for (int cycle = 0; cycle < repeats; cycle++) {
            for (int i = 0; i < spacing.length; i++) {
                final int c = cycle, index = i;
                long scheduled = delay;
                registerInterval(scheduled);
                handler.postDelayed(() -> {
                    if (!valid(g)) return;
                    double root = cap(baseHz + (index % 3) * 137);
                    double[] triad = new double[]{root, cap(root * 1.25), cap(root * 1.5)};
                    boolean left = !stereoWeave || index % 2 == 0;
                    boolean right = !stereoWeave || !left;
                    emitChordPulse(pattern, triad, 190 + currentStrength * 35, audible,
                            left, right, c + 1, index + 1,
                            "mathematical triad spacing " + spacing[index]);
                }, scheduled);
                delay += spacing[i] * unitMs;
            }
            delay += unitMs * 3L;
        }
        return delay;
    }

    private long scheduleScalar(Pattern pattern, double base, boolean audible, int unitMs,
                                int repeats, int g) {
        int[] intervals = {1,2,1,3,2,5,3,8};
        long delay = 0;
        for (int cycle = 0; cycle < repeats; cycle++) {
            for (int i = 0; i < intervals.length; i++) {
                final int c = cycle, index = i;
                long scheduled = delay;
                registerInterval(scheduled);
                handler.postDelayed(() -> {
                    if (!valid(g)) return;
                    double offset = 53 + currentStrength * 23;
                    double leftHz = cap(Math.max(200, base - offset));
                    double rightHz = cap(base + offset);
                    visual(true);
                    if (audible) audio.playChord(new double[]{leftHz, rightHz},
                            210 + currentStrength * 25, amplitude(), true, true);
                    BeaconPulse p = createPulse(pattern, base, 210 + currentStrength * 25,
                            true, audible,
                            "fictional opposing-channel timing marker " + intervals[index],
                            c + 1, index + 1);
                    dispatch(p);
                    handler.postDelayed(() -> { if (valid(g)) visual(false); }, 210 + currentStrength * 25L);
                }, scheduled);
                delay += Math.max(220, intervals[i] * unitMs);
            }
            delay += unitMs * 2L;
        }
        return delay;
    }

    private long scheduleCompletionProbe(Pattern pattern, double baseHz, boolean audible,
                                         int unitMs, int repeats, boolean stereoWeave, int g) {
        int[] full = {2,3,5,7,11};
        long delay = 0;
        for (int cycle = 0; cycle < repeats; cycle++) {
            for (int i = 0; i < full.length - 1; i++) {
                final int c = cycle, index = i;
                long scheduled = delay;
                registerInterval(scheduled);
                handler.postDelayed(() -> {
                    if (!valid(g)) return;
                    double hz = cap(baseHz + index * 83);
                    boolean left = !stereoWeave || index % 2 == 0;
                    boolean right = !stereoWeave || !left;
                    emitTonePulse(pattern, hz, 170 + currentStrength * 30, audible,
                            left, right, c + 1, index + 1,
                            "completion probe element " + full[index]);
                }, scheduled);
                delay += full[i] * unitMs;
            }
            delay += full[full.length - 1] * unitMs;
            delay += unitMs * 2L;
        }
        return delay;
    }

    private void emitTonePulse(Pattern pattern, double hz, int duration, boolean audible,
                               boolean left, boolean right, int cycle, int index, String label) {
        visual(true);
        if (audible) audio.playTone(hz, duration, amplitude(), left, right);
        dispatch(createPulse(pattern, hz, duration, true, audible, label, cycle, index));
        handler.postDelayed(() -> visual(false), duration);
    }

    private void emitChordPulse(Pattern pattern, double[] hz, int duration, boolean audible,
                                boolean left, boolean right, int cycle, int index, String label) {
        visual(true);
        if (audible) audio.playChord(hz, duration, amplitude(), left, right);
        dispatch(createPulse(pattern, hz.length == 0 ? 0 : hz[0], duration,
                true, audible, label, cycle, index));
        handler.postDelayed(() -> visual(false), duration);
    }

    private BeaconPulse createPulse(Pattern pattern, double hz, int duration, boolean light,
                                    boolean audible, String label, int cycle, int index) {
        BeaconPulse p = new BeaconPulse();
        p.timestampMs = System.currentTimeMillis();
        p.pattern = pattern.label;
        p.frequencyHz = hz;
        p.durationMs = duration;
        p.lightOn = light;
        p.audible = audible;
        p.label = label;
        p.cycle = cycle;
        p.index = index;
        p.strength = currentStrength;
        return p;
    }

    private void dispatch(BeaconPulse p) {
        emittedTimes.add(p.timestampMs);
        if (listener != null) listener.onBeaconPulse(p);
    }

    private void registerInterval(long scheduledMs) {
        if (!expectedIntervals.isEmpty() || scheduledMs > 0) {
            long previous = 0;
            for (Long interval : expectedIntervals) previous += interval;
            long next = scheduledMs - previous;
            if (next > 0) expectedIntervals.add(next);
        }
    }

    private double[] harmonicSet(double fundamental, int count) {
        int n = clamp(count, 1, 5);
        List<Double> values = new ArrayList<>();
        double[] ratios = {1.0, 1.25, 1.5, 2.0, 2.5};
        for (int i = 0; i < n; i++) {
            double f = cap(fundamental * ratios[i]);
            if (f >= 40 && f <= 19500) values.add(f);
        }
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i);
        return out;
    }

    private float amplitude() { return Math.min(0.09f, 0.035f + currentStrength * 0.013f); }
    private double cap(double hz) { return Math.max(80, Math.min(19500, hz)); }
    private void visual(boolean on) { if (listener != null) listener.onVisualPulse(on); }

    private void finishLater(Pattern pattern, long delay, int responseWindowMs, int g) {
        handler.postDelayed(() -> {
            if (!valid(g)) return;
            running = false;
            visual(false);
            emissionEndMs = System.currentTimeMillis();
            responseWindowEndMs = emissionEndMs + clamp(responseWindowMs, 15000, 180000);
            if (listener != null) {
                listener.onBeaconState(pattern.label +
                        " emitted. Passive listening active for " +
                        ((responseWindowEndMs - emissionEndMs) / 1000) + " seconds.");
            }
        }, delay);
    }

    private boolean valid(int g) { return running && generation == g; }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}