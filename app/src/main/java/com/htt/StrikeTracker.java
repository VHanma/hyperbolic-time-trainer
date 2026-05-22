package com.htt;

import android.graphics.PointF;
import java.util.ArrayList;
import java.util.List;

/**
 * Optical-flow strike tracker using frame-delta pixel centroid tracking.
 * Speed  = keypoint displacement / frame_dt (scaled px→m/s via PPM constant)
 * Power  = peak_speed * technique_multiplier
 * Level  = composite power level (0–9000+, DBZ-inspired scale)
 */
public class StrikeTracker {

    // Pixels-per-meter calibration at ~60cm arm's length from phone
    private static final float PPM = 420f;
    private static final float MOTION_THRESHOLD = 18f;
    private static final float MIN_STRIKE_SPEED  = 1.2f;  // m/s
    private static final int   WINDOW            = 8;

    public static class StrikeResult {
        public float speedMs;
        public float powerScore;     // 0–100
        public float techniqueScore; // 0–100
        public float powerLevel;     // 0–9000+ (composite DBZ scale)
        public long  timestampMs;
        public boolean isPerfect;    // tech>=85 && speed>=7

        public String levelLabel() {
            if (powerLevel >= 9000) return "OVER 9000!!!";
            if (powerLevel >= 6000) return "ELITE";
            if (powerLevel >= 3000) return "ADVANCED";
            if (powerLevel >= 1000) return "TRAINED";
            return "ROOKIE";
        }
    }

    private final PointF[] centroids  = new PointF[WINDOW];
    private final long[]   timestamps = new long[WINDOW];
    private int head = 0, count = 0;

    private StrikeResult lastStrike;
    private StrikeResult pbSpeed;
    private StrikeResult pbPower;
    private StrikeResult pbLevel;
    private int totalStrikes = 0;

    private final StrikeDatabase db;
    private final StrikeListener listener;

    public interface StrikeListener {
        void onStrike(StrikeResult result, boolean isPersonalBest);
    }

    public StrikeTracker(StrikeDatabase db, StrikeListener listener) {
        this.db = db;
        this.listener = listener;
        for (int i = 0; i < WINDOW; i++) centroids[i] = new PointF(0, 0);
        if (db != null) {
            pbSpeed = db.getPersonalBest("speed");
            pbPower = db.getPersonalBest("power");
            pbLevel = db.getPersonalBest("level");
        }
    }

    public StrikeResult processFrame(int[] prev, int[] curr, int w, int h) {
        if (prev == null || curr == null) return null;
        PointF c = motionCentroid(prev, curr, w, h);
        long now = System.currentTimeMillis();
        centroids[head] = c;
        timestamps[head] = now;
        head = (head + 1) % WINDOW;
        if (count < WINDOW) count++;
        return detect();
    }

    private PointF motionCentroid(int[] prev, int[] curr, int w, int h) {
        float sx = 0, sy = 0, sw = 0;
        for (int y = 4; y < h - 4; y += 4) {
            for (int x = 4; x < w - 4; x += 4) {
                int i = y * w + x;
                float d = pixelDiff(prev[i], curr[i]);
                if (d > MOTION_THRESHOLD) { sx += x * d; sy += y * d; sw += d; }
            }
        }
        return sw < 1f ? new PointF(0, 0) : new PointF(sx / sw, sy / sw);
    }

    private float pixelDiff(int a, int b) {
        return (Math.abs(((a>>16)&0xFF) - ((b>>16)&0xFF)) +
                Math.abs(((a>>8)&0xFF)  - ((b>>8)&0xFF))  +
                Math.abs((a&0xFF)       - (b&0xFF))) / 3f;
    }

    private StrikeResult detect() {
        if (count < 3) return null;
        List<PointF> pts = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = ((head - 1 - i) + WINDOW) % WINDOW;
            PointF p = centroids[idx];
            if (p.x != 0 || p.y != 0) { pts.add(0, p); times.add(0, timestamps[idx]); }
        }
        if (pts.size() < 2) return null;

        float peakPx = 0;
        for (int i = 1; i < pts.size(); i++) {
            float d = dist(pts.get(i-1), pts.get(i));
            long dt = times.get(i) - times.get(i-1);
            if (dt > 0) peakPx = Math.max(peakPx, d / dt * 1000f);
        }

        float speed = peakPx / PPM;
        if (speed < MIN_STRIKE_SPEED) return null;

        float tech  = technique(pts);
        float power = Math.min(100f, (speed / 15f) * 100f * (tech / 100f));
        float level = computePowerLevel(speed, power, tech);

        StrikeResult r = new StrikeResult();
        r.speedMs        = speed;
        r.powerScore     = power;
        r.techniqueScore = tech;
        r.powerLevel     = level;
        r.timestampMs    = System.currentTimeMillis();
        r.isPerfect      = tech >= 85f && speed >= 7f;

        lastStrike = r;
        totalStrikes++;

        boolean isPB = false;
        if (pbSpeed == null || speed > pbSpeed.speedMs) { pbSpeed = r; isPB = true; }
        if (pbPower == null || power > pbPower.powerScore) { pbPower = r; isPB = true; }
        if (pbLevel == null || level > pbLevel.powerLevel) { pbLevel = r; isPB = true; }

        if (db != null) db.saveStrike(r);
        if (listener != null) listener.onStrike(r, isPB);
        count = 0;
        return r;
    }

    /**
     * Power level: 0–9000+ composite scale.
     * Speed contributes 50%, power 30%, technique 20%.
     * Max theoretical: elite boxer ~12m/s = ~8500+.
     */
    private float computePowerLevel(float speed, float power, float tech) {
        float speedContrib = (speed / 15f) * 4500f;
        float powerContrib = (power / 100f) * 2700f;
        float techContrib  = (tech  / 100f) * 1800f;
        return speedContrib + powerContrib + techContrib;
    }

    private float technique(List<PointF> pts) {
        if (pts.size() < 2) return 50f;
        PointF s = pts.get(0), e = pts.get(pts.size()-1);
        float direct = dist(s, e), path = 0;
        for (int i = 1; i < pts.size(); i++) path += dist(pts.get(i-1), pts.get(i));
        if (path < 1f) return 50f;
        float straight = direct / path;
        float accel = 0;
        if (pts.size() >= 4) {
            if (dist(pts.get(pts.size()-2), pts.get(pts.size()-1)) >
                dist(pts.get(0), pts.get(1))) accel = 15f;
        }
        return Math.min(100f, straight * 85f + accel);
    }

    private float dist(PointF a, PointF b) {
        float dx = a.x-b.x, dy = a.y-b.y;
        return (float) Math.sqrt(dx*dx + dy*dy);
    }

    public StrikeResult getLastStrike()     { return lastStrike; }
    public StrikeResult getPbSpeed()        { return pbSpeed; }
    public StrikeResult getPbPower()        { return pbPower; }
    public StrikeResult getPbLevel()        { return pbLevel; }
    public int getTotalStrikes()            { return totalStrikes; }
}
