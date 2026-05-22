package com.htt;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

/**
 * Pose-based strike detector.
 * Tracks wrist velocity relative to shoulder guard, classifies punch type,
 * scores technique via hip rotation + shoulder alignment + trajectory linearity.
 */
public class StrikeTracker {

    public interface StrikeListener {
        void onStrike(StrikeResult result, boolean isPersonalBest);
    }

    public static class StrikeResult {
        public float speedMs;           // peak wrist speed m/s
        public float powerScore;        // 0–100
        public float techniqueScore;    // 0–100
        public float powerLevel;        // 0–9000+
        public String punchType;        // JAB / CROSS / HOOK / UPPERCUT
        public String techniqueNote;    // coaching cue
        public long  timestampMs;
        public boolean isPerfect;       // tech>=85 && speed>=6

        public String levelLabel() {
            if (powerLevel >= 9000) return "OVER 9000!!!";
            if (powerLevel >= 6000) return "ELITE";
            if (powerLevel >= 3000) return "ADVANCED";
            if (powerLevel >= 1000) return "TRAINED";
            return "ROOKIE";
        }
    }

    // Pixels-per-meter at ~60cm arm's length (calibrated to 640px wide frame)
    private static final float PPM = 420f;
    private static final float STRIKE_THRESHOLD_MS = 1.8f; // min m/s to count

    // Strike phase state machine
    private static final int IDLE    = 0;
    private static final int LOADING = 1;
    private static final int FIRING  = 2;

    private int phase = IDLE;
    private float peakSpeed = 0;
    private float peakTech  = 0;
    private String punchType = "JAB";

    // Previous frame landmarks
    private float prevRWristX, prevRWristY;
    private float prevLWristX, prevLWristY;
    private long  prevTimeMs = 0;

    // Personal bests (in-memory, loaded from DB at init)
    private StrikeResult pbSpeed, pbPower, pbLevel;

    private final StrikeDatabase db;
    private final StrikeListener listener;

    public StrikeTracker(StrikeDatabase db, StrikeListener listener) {
        this.db = db;
        this.listener = listener;
        if (db != null) {
            pbSpeed = db.getPersonalBest("speed");
            pbPower = db.getPersonalBest("power");
            pbLevel = db.getPersonalBest("level");
        }
    }

    public void processPose(Pose pose) {
        if (pose == null) return;
        long now = System.currentTimeMillis();
        if (prevTimeMs == 0) { prevTimeMs = now; cacheWrists(pose); return; }

        float dt = (now - prevTimeMs) / 1000f;
        if (dt < 0.01f) return;
        prevTimeMs = now;

        PoseLandmark rWrist  = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
        PoseLandmark lWrist  = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
        PoseLandmark rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rHip    = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
        PoseLandmark lHip    = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark rElbow  = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
        PoseLandmark lElbow  = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);

        if (rWrist == null || lWrist == null || rShoulder == null || lShoulder == null) {
            cacheWrists(pose); return;
        }

        // Wrist speeds (px/s → m/s)
        float rDx = (rWrist.getPosition().x - prevRWristX) / dt;
        float rDy = (rWrist.getPosition().y - prevRWristY) / dt;
        float lDx = (lWrist.getPosition().x - prevLWristX) / dt;
        float lDy = (lWrist.getPosition().y - prevLWristY) / dt;
        float rSpeed = (float) Math.sqrt(rDx*rDx + rDy*rDy) / PPM;
        float lSpeed = (float) Math.sqrt(lDx*lDx + lDy*lDy) / PPM;

        float speed = Math.max(rSpeed, lSpeed);
        boolean rightHand = rSpeed >= lSpeed;

        switch (phase) {
            case IDLE:
                if (speed > STRIKE_THRESHOLD_MS) {
                    phase = FIRING;
                    peakSpeed = speed;
                    punchType = classifyPunch(
                            rightHand ? rDx : lDx,
                            rightHand ? rDy : lDy,
                            rightHand, rShoulder, lShoulder, rElbow, lElbow);
                    peakTech = scoreTechnique(pose, rightHand, rDx, rDy, lDx, lDy);
                }
                break;

            case FIRING:
                if (speed > peakSpeed) {
                    peakSpeed = speed;
                    punchType = classifyPunch(
                            rightHand ? rDx : lDx,
                            rightHand ? rDy : lDy,
                            rightHand, rShoulder, lShoulder, rElbow, lElbow);
                    peakTech = scoreTechnique(pose, rightHand, rDx, rDy, lDx, lDy);
                } else if (speed < STRIKE_THRESHOLD_MS * 0.5f) {
                    // Deceleration — strike complete
                    emitStrike(peakSpeed, peakTech, punchType, pose, rHip, lHip);
                    phase = IDLE;
                    peakSpeed = 0;
                }
                break;
        }

        cacheWrists(pose);
    }

    private void emitStrike(float speed, float tech, String punch,
                            Pose pose, PoseLandmark rHip, PoseLandmark lHip) {
        float power = computePower(speed, tech, rHip, lHip);
        float level = computePowerLevel(speed, power, tech);

        StrikeResult r = new StrikeResult();
        r.speedMs        = speed;
        r.powerScore     = power;
        r.techniqueScore = tech;
        r.powerLevel     = level;
        r.punchType      = punch;
        r.techniqueNote  = techniqueNote(tech, speed, punch);
        r.timestampMs    = System.currentTimeMillis();
        r.isPerfect      = tech >= 85f && speed >= 6f;

        boolean isPB = false;
        if (pbSpeed == null || speed > pbSpeed.speedMs)   { pbSpeed = r; isPB = true; }
        if (pbPower == null || power > pbPower.powerScore) { pbPower = r; isPB = true; }
        if (pbLevel == null || level > pbLevel.powerLevel) { pbLevel = r; isPB = true; }

        if (db != null) db.saveStrike(r);
        if (listener != null) listener.onStrike(r, isPB);
    }

    // ── Punch classification ──────────────────────────────────────────────────

    private String classifyPunch(float dx, float dy,
                                  boolean rightHand,
                                  PoseLandmark rShoulder, PoseLandmark lShoulder,
                                  PoseLandmark rElbow, PoseLandmark lElbow) {
        // dy positive = moving down in image coords (uppercut feels upward on body)
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);

        // Uppercut: wrist moving upward (negative dy in image) faster than horizontal
        if (dy < 0 && absDy > absDx * 0.8f) return "UPPERCUT";

        // Hook: significant lateral component AND elbow bent outward
        if (rElbow != null && lElbow != null) {
            float elbowLateral = rightHand
                    ? Math.abs(rElbow.getPosition().x - rShoulder.getPosition().x)
                    : Math.abs(lElbow.getPosition().x - lShoulder.getPosition().x);
            if (absDy < absDx * 0.6f && elbowLateral > 40f) return "HOOK";
        }

        // Jab (lead hand) vs Cross (rear hand) — right-handed stance: jab=left, cross=right
        return rightHand ? "CROSS" : "JAB";
    }

    // ── Technique scoring ─────────────────────────────────────────────────────

    private float scoreTechnique(Pose pose, boolean rightHand,
                                  float rDx, float rDy, float lDx, float lDy) {
        float score = 50f; // baseline

        PoseLandmark rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rHip      = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
        PoseLandmark lHip      = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark rElbow    = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
        PoseLandmark lElbow    = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);

        if (rShoulder == null || lShoulder == null) return score;

        // 1. Shoulder rotation: punching shoulder should rotate forward (+15 pts)
        float shoulderAngle = Math.abs(
                rShoulder.getPosition3D().getZ() - lShoulder.getPosition3D().getZ());
        score += Math.min(15f, shoulderAngle * 0.5f);

        // 2. Hip rotation present (+15 pts)
        if (rHip != null && lHip != null) {
            float hipAngle = Math.abs(rHip.getPosition3D().getZ() - lHip.getPosition3D().getZ());
            score += Math.min(15f, hipAngle * 0.5f);
        }

        // 3. Arm extension — elbow below shoulder level on contact (+10 pts)
        PoseLandmark elbow = rightHand ? rElbow : lElbow;
        PoseLandmark shoulder = rightHand ? rShoulder : lShoulder;
        if (elbow != null && shoulder != null) {
            float extRatio = elbow.getPosition().y / (shoulder.getPosition().y + 1f);
            if (extRatio > 0.85f) score += 10f;
        }

        // 4. Guard hand stays up — non-punching wrist near its shoulder (+10 pts)
        PoseLandmark guardWrist  = rightHand
                ? pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
                : pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
        PoseLandmark guardShoulder = rightHand ? lShoulder : rShoulder;
        if (guardWrist != null) {
            float guardDrop = guardWrist.getPosition().y - guardShoulder.getPosition().y;
            if (guardDrop < 60f) score += 10f;
        }

        return Math.min(100f, score);
    }

    // ── Power & level ─────────────────────────────────────────────────────────

    private float computePower(float speed, float tech,
                               PoseLandmark rHip, PoseLandmark lHip) {
        float hipBonus = 1f;
        if (rHip != null && lHip != null) {
            float hipRot = Math.abs(rHip.getPosition3D().getZ() - lHip.getPosition3D().getZ());
            hipBonus = 1f + Math.min(0.3f, hipRot * 0.01f);
        }
        return Math.min(100f, (speed / 15f) * 100f * (tech / 100f) * hipBonus);
    }

    private float computePowerLevel(float speed, float power, float tech) {
        return (speed / 15f) * 4500f + (power / 100f) * 2700f + (tech / 100f) * 1800f;
    }

    // ── Coaching note ─────────────────────────────────────────────────────────

    private String techniqueNote(float tech, float speed, String punch) {
        if (tech >= 85f) return "PERFECT " + punch + "!";
        if (tech >= 65f && speed < 4f) return "More hip drive";
        if (tech >= 65f) return "Good form";
        if (tech < 45f) return "Rotate hips + guard up";
        return "Extend fully on " + punch.toLowerCase();
    }

    private void cacheWrists(Pose pose) {
        PoseLandmark rW = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
        PoseLandmark lW = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
        if (rW != null) { prevRWristX = rW.getPosition().x; prevRWristY = rW.getPosition().y; }
        if (lW != null) { prevLWristX = lW.getPosition().x; prevLWristY = lW.getPosition().y; }
    }
}
