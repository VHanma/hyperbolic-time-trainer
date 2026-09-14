package com.htt;

import android.content.SharedPreferences;
import android.graphics.PointF;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Session-calibrated markerless strike tracker.
 *
 * The detector is deliberately conservative. It learns the user's guard first,
 * tracks each arm independently through guard -> extension -> return, and rejects
 * motion that does not form a complete strike. Speed and force remain camera-based
 * estimates rather than direct impact measurements.
 */
public final class StrikeTracker {
    public interface StrikeListener {
        void onStrike(StrikeResult result, boolean isPersonalBest);
    }

    public static final class StrikeResult {
        public float speedMs;
        public float speedMph;
        public float speedLowMph;
        public float speedHighMph;
        public float accelerationMs2;
        public float forceLowLbf;
        public float forceHighLbf;
        public float momentumNs;
        public float kineticEnergyJ;
        public float techniqueScore;
        public float chainScore;
        public float guardScore;
        public float balanceScore;
        public float confidence;
        public float frameRate;
        public float guardReturnMs;
        public float powerScore;
        public float powerLevel;
        public String punchType;
        public String techniqueNote;
        public long timestampMs;
        public boolean isPerfect;

        public String confidenceLabel() {
            if (confidence >= 84f) return "HIGH";
            if (confidence >= 68f) return "MEDIUM";
            return "LOW";
        }

        public String levelLabel() {
            if (techniqueScore >= 90f && speedMph >= 20f) return "ELITE REP";
            if (techniqueScore >= 82f) return "CONNECTED";
            if (techniqueScore >= 68f) return "SOLID";
            return "REBUILD";
        }
    }

    private enum ArmPhase { GUARD, EXTENDING, RETURNING, COOLDOWN }

    private static final class ArmTrack {
        final boolean right;
        ArmPhase phase = ArmPhase.GUARD;
        final Deque<Float> speedSamples = new ArrayDeque<>();
        float prevRawX, prevRawY;
        float prevRelX, prevRelY;
        float prevDist;
        float prevAngle;
        long prevTimeMs;
        int guardFrames;
        float guardRelX, guardRelY, guardDist;
        long strikeStartMs;
        long peakTimeMs;
        float startRelX, startRelY, startDist, startAngle, startElbow;
        float peakRelX, peakRelY, peakDist, peakAngle;
        float peakSpeed, peakAcceleration;
        float peakTangential;
        float maxPath;
        float minElbow, maxElbow;
        float peakLikelihood;
        float freeHandGuardAtPeak;
        float balanceAtPeak;
        float chainAtPeak;
        float pathQuality;
        boolean returnedToGuard;

        ArmTrack(boolean right) { this.right = right; }

        void resetStrike() {
            strikeStartMs = 0L;
            peakTimeMs = 0L;
            startRelX = startRelY = startDist = startAngle = startElbow = 0f;
            peakRelX = peakRelY = peakDist = peakAngle = 0f;
            peakSpeed = peakAcceleration = peakTangential = maxPath = 0f;
            minElbow = 180f;
            maxElbow = 0f;
            peakLikelihood = freeHandGuardAtPeak = balanceAtPeak = chainAtPeak = pathQuality = 0f;
            returnedToGuard = false;
        }
    }

    private final StrikeDatabase db;
    private final StrikeListener listener;
    private final float bodyMassKg;
    private final float shoulderWidthMeters;
    private final boolean orthodox;
    private final ArmTrack rightArm = new ArmTrack(true);
    private final ArmTrack leftArm = new ArmTrack(false);
    private long prevFrameMs;
    private float frameRate = 0f;
    private float pixelsPerMeter = 420f;
    private float ppmStability = 0.65f;
    private float prevPpm = 420f;
    private float prevBodyX, prevBodyY;
    private float bodySpeed;
    private boolean calibrated;
    private int calibrationFrames;
    private long stableCalibrationStartMs;
    private float calibrationLikelihoodSum;
    private float calRightX, calRightY, calRightDist;
    private float calLeftX, calLeftY, calLeftDist;
    private float calibrationQuality;
    private float prevHipTwist;
    private float prevShoulderTwist;
    private long lastHipMotionMs;
    private long lastShoulderMotionMs;
    private long lastStatusMs;
    private long statusHoldUntilMs;
    private String liveStatus = "FINDING FIGHTER";
    private String liveReadout = "Stand in guard with shoulders, elbows, wrists, hips and feet visible";
    private StrikeResult pbSpeed;
    private StrikeResult pbPower;

    public StrikeTracker(StrikeDatabase db, SharedPreferences prefs, StrikeListener listener) {
        this.db = db;
        this.listener = listener;
        float pounds = prefs.getFloat("body_weight_lb", 150f);
        float shoulderIn = prefs.getFloat("shoulder_width_in", 17f);
        this.bodyMassKg = clamp(pounds * 0.45359237f, 40f, 180f);
        this.shoulderWidthMeters = clamp(shoulderIn * 0.0254f, 0.30f, 0.65f);
        this.orthodox = prefs.getBoolean("orthodox", true);
        if (db != null) {
            pbSpeed = db.getPersonalBest("speed");
            pbPower = db.getPersonalBest("power");
        }
    }

    public void resetCalibration() {
        calibrated = false;
        calibrationFrames = 0;
        stableCalibrationStartMs = 0L;
        calibrationLikelihoodSum = 0f;
        calRightX = calRightY = calRightDist = 0f;
        calLeftX = calLeftY = calLeftDist = 0f;
        calibrationQuality = 0f;
        rightArm.phase = ArmPhase.GUARD;
        leftArm.phase = ArmPhase.GUARD;
        rightArm.guardFrames = leftArm.guardFrames = 0;
        rightArm.resetStrike();
        leftArm.resetStrike();
        liveStatus = "CALIBRATION RESET";
        liveReadout = "Hold your normal guard still for about two seconds";
    }

    public String getLiveStatus() { return liveStatus; }
    public String getLiveReadout() { return liveReadout; }
    public boolean isCalibrated() { return calibrated; }
    public int getCalibrationPercent() {
        if (calibrated) return 100;
        return Math.min(99, Math.round(calibrationFrames * 100f / 24f));
    }

    public void processPose(Pose pose, long timestampMs) {
        if (pose == null) {
            updateMissing("NO POSE RESULT", timestampMs);
            return;
        }
        PoseLandmark rw = lm(pose, PoseLandmark.RIGHT_WRIST);
        PoseLandmark lw = lm(pose, PoseLandmark.LEFT_WRIST);
        PoseLandmark re = lm(pose, PoseLandmark.RIGHT_ELBOW);
        PoseLandmark le = lm(pose, PoseLandmark.LEFT_ELBOW);
        PoseLandmark rs = lm(pose, PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark ls = lm(pose, PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rh = lm(pose, PoseLandmark.RIGHT_HIP);
        PoseLandmark lh = lm(pose, PoseLandmark.LEFT_HIP);
        PoseLandmark ra = lm(pose, PoseLandmark.RIGHT_ANKLE);
        PoseLandmark la = lm(pose, PoseLandmark.LEFT_ANKLE);
        PoseLandmark nose = lm(pose, PoseLandmark.NOSE);

        if (rw == null || lw == null || re == null || le == null || rs == null || ls == null || rh == null || lh == null) {
            updateMissing("FULL UPPER BODY NOT FOUND", timestampMs);
            return;
        }
        float coreLikelihood = averageLikelihood(rw, lw, re, le, rs, ls, rh, lh);
        if (coreLikelihood < 0.48f) {
            updateMissing(coreLikelihood < 0.25f ? "FIGHTER LOST" : "HANDS OR TORSO UNCERTAIN", timestampMs);
            return;
        }
        PointF rsp = rs.getPosition();
        PointF lsp = ls.getPosition();
        PointF rhp = rh.getPosition();
        PointF lhp = lh.getPosition();
        float shoulderPx = distance(rsp, lsp);
        if (shoulderPx < 35f) {
            updateMissing("MOVE CLOSER: SHOULDERS TOO SMALL", timestampMs);
            return;
        }
        updateFrameRate(timestampMs);
        updateScale(shoulderPx);
        PointF shoulderMid = midpoint(rsp, lsp);
        PointF hipMid = midpoint(rhp, lhp);
        PointF bodyMid = new PointF((shoulderMid.x + hipMid.x) * 0.5f, (shoulderMid.y + hipMid.y) * 0.5f);
        float dt = validDt(timestampMs);
        if (dt <= 0f) {
            cacheFrame(timestampMs, bodyMid, rw, lw);
            return;
        }
        bodySpeed = distance(bodyMid, new PointF(prevBodyX, prevBodyY)) / pixelsPerMeter / dt;
        Basis basis = new Basis(rsp, lsp);
        ArmFrame right = buildArmFrame(rw, re, rs, basis, bodyMid, dt, rightArm);
        ArmFrame left = buildArmFrame(lw, le, ls, basis, bodyMid, dt, leftArm);
        updateRotationTiming(rh, lh, rs, ls, shoulderPx, timestampMs);

        if (!calibrated) {
            calibrate(right, left, coreLikelihood, bodySpeed, timestampMs);
            cacheFrame(timestampMs, bodyMid, rw, lw);
            cacheArm(rightArm, right, timestampMs);
            cacheArm(leftArm, left, timestampMs);
            return;
        }

        processArm(rightArm, right, left, pose, ra, la, nose, timestampMs);
        processArm(leftArm, left, right, pose, ra, la, nose, timestampMs);
        if (timestampMs >= statusHoldUntilMs) {
            String active = activeDescription();
            liveStatus = active.length() > 0 ? active : String.format(Locale.US, "READY  |  %.0f FPS", frameRate);
            liveReadout = String.format(Locale.US,
                    "R %.1f m/s  |  L %.1f m/s  |  scale %.0f%%  |  %s",
                    right.speed, left.speed, ppmStability * 100f,
                    (ra != null && la != null && averageLikelihood(ra, la) > 0.55f) ? "full-body balance" : "upper-body only");
        }
        cacheFrame(timestampMs, bodyMid, rw, lw);
        cacheArm(rightArm, right, timestampMs);
        cacheArm(leftArm, left, timestampMs);
    }

    private static final class Basis {
        final float ux, uy, vx, vy, span;
        Basis(PointF rightShoulder, PointF leftShoulder) {
            float dx = rightShoulder.x - leftShoulder.x;
            float dy = rightShoulder.y - leftShoulder.y;
            span = Math.max(1f, hypot(dx, dy));
            ux = dx / span;
            uy = dy / span;
            vx = -uy;
            vy = ux;
        }
        PointF project(PointF point, PointF origin) {
            float dx = point.x - origin.x;
            float dy = point.y - origin.y;
            return new PointF((dx * ux + dy * uy) / span, (dx * vx + dy * vy) / span);
        }
    }

    private static final class ArmFrame {
        PointF wristRaw;
        float relX, relY, dist, angle;
        float speed, acceleration, radialVelocity, tangentialVelocity, verticalVelocity;
        float elbowAngle;
        float likelihood;
    }

    private ArmFrame buildArmFrame(PoseLandmark wrist, PoseLandmark elbow,
                                   PoseLandmark shoulder, Basis basis, PointF bodyMid,
                                   float dt, ArmTrack track) {
        ArmFrame out = new ArmFrame();
        out.wristRaw = wrist.getPosition();
        PointF rel = basis.project(out.wristRaw, shoulder.getPosition());
        out.relX = rel.x;
        out.relY = rel.y;
        out.dist = hypot(rel.x, rel.y);
        out.angle = (float) Math.atan2(rel.y, rel.x);
        out.elbowAngle = jointAngle(shoulder.getPosition(), elbow.getPosition(), wrist.getPosition());
        out.likelihood = averageLikelihood(wrist, elbow, shoulder);
        if (track.prevTimeMs > 0L && dt > 0f) {
            float bodyDx = bodyMid.x - prevBodyX;
            float bodyDy = bodyMid.y - prevBodyY;
            float dx = (out.wristRaw.x - track.prevRawX) - bodyDx;
            float dy = (out.wristRaw.y - track.prevRawY) - bodyDy;
            float rawSpeed = hypot(dx, dy) / pixelsPerMeter / dt;
            out.speed = medianSmooth(track.speedSamples, clamp(rawSpeed, 0f, 24f));
            float previousSpeed = track.speedSamples.size() >= 2 ? secondLast(track.speedSamples) : 0f;
            out.acceleration = Math.abs(out.speed - previousSpeed) / dt;
            out.radialVelocity = (out.dist - track.prevDist) * shoulderWidthMeters / dt;
            float angularDelta = wrapAngle(out.angle - track.prevAngle);
            out.tangentialVelocity = Math.abs(angularDelta) * Math.max(0.15f, out.dist) * shoulderWidthMeters / dt;
            out.verticalVelocity = (out.relY - track.prevRelY) * shoulderWidthMeters / dt;
        }
        return out;
    }

    private void calibrate(ArmFrame right, ArmFrame left, float likelihood, float currentBodySpeed, long now) {
        boolean quietHands = right.speed < 0.85f && left.speed < 0.85f;
        boolean quietBody = currentBodySpeed < 0.55f;
        boolean goodFrame = likelihood >= 0.67f && right.likelihood >= 0.65f && left.likelihood >= 0.65f;
        if (quietHands && quietBody && goodFrame) {
            if (stableCalibrationStartMs == 0L) stableCalibrationStartMs = now;
            calibrationFrames++;
            calibrationLikelihoodSum += likelihood;
            calRightX += right.relX; calRightY += right.relY; calRightDist += right.dist;
            calLeftX += left.relX; calLeftY += left.relY; calLeftDist += left.dist;
            int percent = Math.min(99, Math.round(calibrationFrames * 100f / 24f));
            liveStatus = "CALIBRATING " + percent + "%";
            liveReadout = "Hold your real guard still. Keep both hands, hips and feet visible.";
            if (calibrationFrames >= 24 && now - stableCalibrationStartMs >= 900L) {
                rightArm.guardRelX = calRightX / calibrationFrames;
                rightArm.guardRelY = calRightY / calibrationFrames;
                rightArm.guardDist = calRightDist / calibrationFrames;
                leftArm.guardRelX = calLeftX / calibrationFrames;
                leftArm.guardRelY = calLeftY / calibrationFrames;
                leftArm.guardDist = calLeftDist / calibrationFrames;
                calibrationQuality = clamp(calibrationLikelihoodSum / calibrationFrames * ppmStability, 0.45f, 1f);
                calibrated = true;
                rightArm.guardFrames = leftArm.guardFrames = 4;
                liveStatus = String.format(Locale.US, "READY  |  %.0f FPS", frameRate);
                liveReadout = "Guard learned. Throw one clean punch and return the hand home.";
            }
        } else {
            if (calibrationFrames < 6) {
                calibrationFrames = 0;
                calibrationLikelihoodSum = 0f;
                calRightX = calRightY = calRightDist = 0f;
                calLeftX = calLeftY = calLeftDist = 0f;
                stableCalibrationStartMs = 0L;
            }
            liveStatus = !goodFrame ? "CALIBRATION: REFRAME FULL BODY" : "CALIBRATION: HOLD STILL IN GUARD";
            liveReadout = quietHands ? "Keep hips and camera steady" : "Stop punching until calibration completes";
        }
    }

    private void processArm(ArmTrack track, ArmFrame frame, ArmFrame other,
                            Pose pose, PoseLandmark ra, PoseLandmark la,
                            PoseLandmark nose, long now) {
        boolean inGuard = isInGuard(track, frame);
        if (inGuard) track.guardFrames = Math.min(10, track.guardFrames + 1);
        else track.guardFrames = Math.max(0, track.guardFrames - 1);
        switch (track.phase) {
            case GUARD:
                if (track.guardFrames >= 2 && shouldStartStrike(track, frame)) {
                    startStrike(track, frame, other, pose, ra, la, nose, now);
                }
                break;
            case EXTENDING:
                updateStrikePeak(track, frame, other, pose, ra, la, nose, now);
                long duration = now - track.strikeStartMs;
                boolean reversed = frame.radialVelocity < -0.35f && track.peakSpeed > 2.5f;
                boolean slowed = frame.speed < Math.max(0.85f, track.peakSpeed * 0.38f);
                if ((duration >= 90L && (reversed || slowed)) || duration > 620L) {
                    track.phase = ArmPhase.RETURNING;
                    track.peakTimeMs = track.peakTimeMs == 0L ? now : track.peakTimeMs;
                }
                break;
            case RETURNING:
                if (inGuard) {
                    track.returnedToGuard = true;
                    emit(track, now);
                    track.phase = ArmPhase.GUARD;
                    track.guardFrames = 3;
                    track.resetStrike();
                } else if (now - track.peakTimeMs > 820L) {
                    track.returnedToGuard = false;
                    emit(track, now);
                    track.phase = ArmPhase.COOLDOWN;
                    track.resetStrike();
                }
                break;
            case COOLDOWN:
                if (inGuard) {
                    track.phase = ArmPhase.GUARD;
                    track.guardFrames = 3;
                }
                break;
        }
    }

    private boolean shouldStartStrike(ArmTrack track, ArmFrame frame) {
        if (frame.likelihood < 0.62f || frame.speed < 2.05f) return false;
        float awayFromGuard = distance(frame.relX, frame.relY, track.guardRelX, track.guardRelY);
        boolean straightSignal = frame.radialVelocity > 0.72f;
        boolean arcSignal = frame.tangentialVelocity > 0.88f && frame.elbowAngle < 158f;
        boolean verticalSignal = Math.abs(frame.verticalVelocity) > 0.72f && frame.elbowAngle < 150f;
        return awayFromGuard > 0.075f && (straightSignal || arcSignal || verticalSignal);
    }

    private void startStrike(ArmTrack track, ArmFrame frame, ArmFrame other, Pose pose,
                             PoseLandmark ra, PoseLandmark la, PoseLandmark nose, long now) {
        track.resetStrike();
        track.phase = ArmPhase.EXTENDING;
        track.strikeStartMs = now;
        track.startRelX = frame.relX;
        track.startRelY = frame.relY;
        track.startDist = frame.dist;
        track.startAngle = frame.angle;
        track.startElbow = frame.elbowAngle;
        track.minElbow = frame.elbowAngle;
        track.maxElbow = frame.elbowAngle;
        updateStrikePeak(track, frame, other, pose, ra, la, nose, now);
    }

    private void updateStrikePeak(ArmTrack track, ArmFrame frame, ArmFrame other, Pose pose,
                                  PoseLandmark ra, PoseLandmark la, PoseLandmark nose, long now) {
        float displacement = distance(frame.relX, frame.relY, track.startRelX, track.startRelY);
        track.maxPath = Math.max(track.maxPath, displacement);
        track.minElbow = Math.min(track.minElbow, frame.elbowAngle);
        track.maxElbow = Math.max(track.maxElbow, frame.elbowAngle);
        track.peakTangential = Math.max(track.peakTangential, frame.tangentialVelocity);
        if (frame.speed >= track.peakSpeed) {
            track.peakSpeed = frame.speed;
            track.peakAcceleration = frame.acceleration;
            track.peakTimeMs = now;
            track.peakRelX = frame.relX;
            track.peakRelY = frame.relY;
            track.peakDist = frame.dist;
            track.peakAngle = frame.angle;
            track.peakLikelihood = frame.likelihood;
            track.freeHandGuardAtPeak = freeHandGuardScore(track.right ? leftArm : rightArm, other);
            track.balanceAtPeak = balanceScore(pose, ra, la, nose);
            track.chainAtPeak = chainScore(track.right, track.strikeStartMs);
            track.pathQuality = pathQuality(track, frame);
        }
    }

    private void emit(ArmTrack track, long now) {
        long strikeDuration = Math.max(1L, track.peakTimeMs - track.strikeStartMs);
        if (track.peakSpeed < 2.45f || track.maxPath < 0.16f || strikeDuration < 55L || strikeDuration > 700L) {
            liveStatus = "IGNORED: MOTION WAS NOT A COMPLETE STRIKE";
            liveReadout = String.format(Locale.US, "peak %.1f m/s  |  path %.2f shoulder widths", track.peakSpeed, track.maxPath);
            statusHoldUntilMs = now + 850L;
            return;
        }
        String punch = classify(track);
        float fpsQuality = clamp((frameRate - 10f) / 15f, 0.35f, 1f);
        float pathConfidence = clamp((track.maxPath - 0.12f) / 0.38f, 0.35f, 1f);
        float confidence = 100f * clamp(
                0.28f * track.peakLikelihood +
                0.20f * calibrationQuality +
                0.18f * ppmStability +
                0.18f * fpsQuality +
                0.16f * pathConfidence,
                0f, 1f);
        if (confidence < 61f) {
            liveStatus = "IGNORED: TRACKING CONFIDENCE TOO LOW";
            liveReadout = String.format(Locale.US, "confidence %.0f%%  |  %.0f FPS", confidence, frameRate);
            statusHoldUntilMs = now + 850L;
            return;
        }
        float guardReturnMs = track.returnedToGuard ? Math.max(0f, now - track.peakTimeMs) : 999f;
        float guardScore = track.returnedToGuard
                ? clamp(100f - Math.max(0f, guardReturnMs - 180f) * 0.085f, 55f, 100f)
                : 34f;
        guardScore = clamp(guardScore * 0.62f + track.freeHandGuardAtPeak * 0.38f, 0f, 100f);
        float technique = clamp(
                track.chainAtPeak * 0.27f +
                guardScore * 0.23f +
                track.balanceAtPeak * 0.20f +
                track.pathQuality * 0.20f +
                extensionScore(track, punch) * 0.10f,
                0f, 100f);
        float samplingUncertainty = clamp((24f - frameRate) / 70f, 0.04f, 0.24f);
        float speedLowMs = track.peakSpeed * 0.94f;
        float speedHighMs = track.peakSpeed * (1.07f + samplingUncertainty);
        float speedMidMs = (speedLowMs + speedHighMs) * 0.5f;

        StrikeResult result = new StrikeResult();
        result.speedMs = speedMidMs;
        result.speedMph = speedMidMs * 2.2369363f;
        result.speedLowMph = speedLowMs * 2.2369363f;
        result.speedHighMph = speedHighMs * 2.2369363f;
        result.accelerationMs2 = track.peakAcceleration;
        result.techniqueScore = technique;
        result.chainScore = track.chainAtPeak;
        result.guardScore = guardScore;
        result.balanceScore = track.balanceAtPeak;
        result.confidence = confidence;
        result.frameRate = frameRate;
        result.guardReturnMs = guardReturnMs;
        result.punchType = punch;
        result.timestampMs = now;
        float[] massFractions = effectiveMassRange(punch, technique, track.chainAtPeak);
        float lowMass = bodyMassKg * massFractions[0];
        float highMass = bodyMassKg * massFractions[1];
        result.momentumNs = ((lowMass * speedLowMs) + (highMass * speedHighMs)) * 0.5f;
        result.kineticEnergyJ = 0.5f * ((0.5f * lowMass * speedLowMs * speedLowMs) + (0.5f * highMass * speedHighMs * speedHighMs));
        float lowN = lowMass * speedLowMs / 0.045f;
        float highN = highMass * speedHighMs / 0.018f;
        result.forceLowLbf = clamp(lowN / 4.4482216f, 40f, 2200f);
        result.forceHighLbf = clamp(highN / 4.4482216f, result.forceLowLbf, 2800f);
        result.powerScore = result.forceHighLbf;
        result.powerLevel = result.forceHighLbf;
        result.isPerfect = technique >= 87f && confidence >= 76f && result.speedMs >= 5.2f && track.returnedToGuard;
        result.techniqueNote = coachingNote(result, track);
        boolean personalBest = false;
        if (pbSpeed == null || result.speedMs > pbSpeed.speedMs) { pbSpeed = result; personalBest = true; }
        if (pbPower == null || result.forceHighLbf > pbPower.powerScore) { pbPower = result; personalBest = true; }
        if (db != null) db.saveStrike(result);
        if (listener != null) listener.onStrike(result, personalBest);
        liveStatus = punch + " SCORED  |  " + result.confidenceLabel() + " CONFIDENCE";
        liveReadout = String.format(Locale.US, "%.1f-%.1f MPH  |  return %.0f ms  |  chain %.0f",
                result.speedLowMph, result.speedHighMph, result.guardReturnMs, result.chainScore);
        statusHoldUntilMs = now + 1200L;
    }

    private String classify(ArmTrack track) {
        float dx = track.peakRelX - track.startRelX;
        float dy = track.peakRelY - track.startRelY;
        float distGain = track.peakDist - track.startDist;
        float angleTravel = Math.abs(wrapAngle(track.peakAngle - track.startAngle));
        boolean rear = orthodox ? track.right : !track.right;
        if (dy < -0.20f && Math.abs(dy) > Math.abs(dx) * 0.62f && track.minElbow < 150f) return "UPPERCUT";
        if (rear && dy > 0.17f && distGain > 0.12f && Math.abs(dy) > Math.abs(dx) * 0.42f) return "OVERHAND";
        if ((angleTravel > 0.34f || track.peakTangential > 1.35f) && track.minElbow < 154f && distGain < 0.42f) return "HOOK";
        return rear ? "CROSS" : "JAB";
    }

    private float pathQuality(ArmTrack track, ArmFrame frame) {
        float displacement = distance(frame.relX, frame.relY, track.startRelX, track.startRelY);
        float directness = displacement / Math.max(0.001f, track.maxPath);
        return clamp(55f + directness * 25f + Math.min(20f, frame.speed * 2.2f), 0f, 100f);
    }

    private float extensionScore(ArmTrack track, String punch) {
        float gain = track.peakDist - track.startDist;
        if ("HOOK".equals(punch) || "UPPERCUT".equals(punch)) {
            return clamp(90f - Math.abs(track.minElbow - 105f) * 0.45f, 48f, 96f);
        }
        return clamp(52f + gain * 95f + (track.maxElbow - 130f) * 0.35f, 40f, 100f);
    }

    private float chainScore(boolean right, long handStartMs) {
        long hipLead = lastHipMotionMs == 0L ? 999L : handStartMs - lastHipMotionMs;
        long shoulderLead = lastShoulderMotionMs == 0L ? 999L : handStartMs - lastShoulderMotionMs;
        boolean rear = orthodox ? right : !right;
        float score = rear ? 48f : 58f;
        if (hipLead >= -20L && hipLead <= 260L) score += rear ? 28f : 16f;
        if (shoulderLead >= -20L && shoulderLead <= 190L) score += 20f;
        if (hipLead >= 20L && shoulderLead >= 0L && hipLead >= shoulderLead) score += 6f;
        return clamp(score, 35f, 98f);
    }

    private float freeHandGuardScore(ArmTrack freeTrack, ArmFrame freeFrame) {
        float d = distance(freeFrame.relX, freeFrame.relY, freeTrack.guardRelX, freeTrack.guardRelY);
        return clamp(100f - d * 150f, 20f, 100f);
    }

    private float balanceScore(Pose pose, PoseLandmark ra, PoseLandmark la, PoseLandmark nose) {
        PoseLandmark rh = lm(pose, PoseLandmark.RIGHT_HIP);
        PoseLandmark lh = lm(pose, PoseLandmark.LEFT_HIP);
        PoseLandmark rs = lm(pose, PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark ls = lm(pose, PoseLandmark.LEFT_SHOULDER);
        if (rh == null || lh == null || rs == null || ls == null) return 50f;
        PointF hip = midpoint(rh.getPosition(), lh.getPosition());
        PointF shoulders = midpoint(rs.getPosition(), ls.getPosition());
        float span = distance(rs.getPosition(), ls.getPosition());
        float lean = Math.abs(shoulders.x - hip.x) / Math.max(25f, span);
        float score = 92f - Math.min(40f, lean * 55f);
        if (ra != null && la != null && averageLikelihood(ra, la) > 0.52f) {
            float left = Math.min(ra.getPosition().x, la.getPosition().x);
            float right = Math.max(ra.getPosition().x, la.getPosition().x);
            float stance = Math.max(20f, right - left);
            float margin = stance * 0.18f;
            if (hip.x < left - margin || hip.x > right + margin) score -= 35f;
        } else {
            score -= 8f;
        }
        if (nose != null && nose.getInFrameLikelihood() > 0.5f) {
            float headLean = Math.abs(nose.getPosition().x - hip.x) / Math.max(25f, span);
            if (headLean > 1.15f) score -= 12f;
        }
        return clamp(score, 25f, 98f);
    }

    private float[] effectiveMassRange(String punch, float technique, float chain) {
        float low, high;
        switch (punch) {
            case "JAB": low = 0.032f; high = 0.058f; break;
            case "HOOK": low = 0.052f; high = 0.095f; break;
            case "UPPERCUT": low = 0.050f; high = 0.090f; break;
            case "OVERHAND": low = 0.065f; high = 0.115f; break;
            default: low = 0.058f; high = 0.105f; break;
        }
        float mechanics = 0.72f + 0.28f * ((technique + chain) * 0.005f);
        return new float[]{low * mechanics, high * mechanics};
    }

    private String coachingNote(StrikeResult r, ArmTrack track) {
        if (r.confidence < 68f) return "Tracking marginal. Reframe and repeat cleanly.";
        if (!track.returnedToGuard) return "Punch detected. Bring the hand fully back to guard.";
        if (r.guardScore < 62f) return "Free hand drifted. Protect during the strike and recover home.";
        if (r.balanceScore < 62f) return "Finish over your base instead of falling past the punch.";
        if (r.chainScore < 63f) return "Hip begins. Shoulder follows. Hand arrives last.";
        if (r.techniqueScore >= 88f) return "Connected " + r.punchType.toLowerCase(Locale.US) + ". Balanced and recovered.";
        return "Clean strike. Stay loose, connect the floor, and sharpen the return.";
    }

    private boolean isInGuard(ArmTrack track, ArmFrame frame) {
        float d = distance(frame.relX, frame.relY, track.guardRelX, track.guardRelY);
        return d < 0.29f && frame.speed < 1.25f && frame.elbowAngle < 168f;
    }

    private String activeDescription() {
        if (rightArm.phase == ArmPhase.EXTENDING) return "READING RIGHT-HAND EXTENSION";
        if (leftArm.phase == ArmPhase.EXTENDING) return "READING LEFT-HAND EXTENSION";
        if (rightArm.phase == ArmPhase.RETURNING) return "WAITING FOR RIGHT HAND TO RETURN";
        if (leftArm.phase == ArmPhase.RETURNING) return "WAITING FOR LEFT HAND TO RETURN";
        return "";
    }

    private void updateRotationTiming(PoseLandmark rh, PoseLandmark lh, PoseLandmark rs,
                                      PoseLandmark ls, float shoulderPx, long now) {
        float hipTwist = (rh.getPosition3D().getZ() - lh.getPosition3D().getZ()) / Math.max(25f, shoulderPx);
        float shoulderTwist = (rs.getPosition3D().getZ() - ls.getPosition3D().getZ()) / Math.max(25f, shoulderPx);
        if (Math.abs(hipTwist - prevHipTwist) > 0.018f) lastHipMotionMs = now;
        if (Math.abs(shoulderTwist - prevShoulderTwist) > 0.018f) lastShoulderMotionMs = now;
        prevHipTwist = hipTwist;
        prevShoulderTwist = shoulderTwist;
    }

    private void updateFrameRate(long now) {
        if (prevFrameMs > 0L && now > prevFrameMs) {
            float instant = 1000f / Math.max(1f, now - prevFrameMs);
            instant = clamp(instant, 1f, 90f);
            frameRate = frameRate <= 0f ? instant : frameRate * 0.88f + instant * 0.12f;
        }
    }

    private void updateScale(float shoulderPx) {
        float candidate = clamp(shoulderPx / shoulderWidthMeters, 100f, 1800f);
        pixelsPerMeter = pixelsPerMeter * 0.88f + candidate * 0.12f;
        float ratio = Math.abs(candidate - prevPpm) / Math.max(1f, prevPpm);
        ppmStability = clamp(ppmStability * 0.92f + Math.max(0f, 1f - ratio * 5f) * 0.08f, 0.25f, 1f);
        prevPpm = candidate;
    }

    private float validDt(long now) {
        if (prevFrameMs <= 0L || now <= prevFrameMs) return -1f;
        float dt = (now - prevFrameMs) / 1000f;
        return (dt < 0.010f || dt > 0.25f) ? -1f : dt;
    }

    private void updateMissing(String reason, long now) {
        if (now - lastStatusMs > 180L) {
            liveStatus = reason;
            liveReadout = "Use bright light. Keep the full body large in frame at a 30-45 degree angle.";
            lastStatusMs = now;
        }
        rightArm.phase = ArmPhase.GUARD;
        leftArm.phase = ArmPhase.GUARD;
        rightArm.guardFrames = leftArm.guardFrames = 0;
        rightArm.prevTimeMs = leftArm.prevTimeMs = 0L;
        rightArm.resetStrike();
        leftArm.resetStrike();
        prevFrameMs = now;
    }

    private void cacheFrame(long now, PointF bodyMid, PoseLandmark rw, PoseLandmark lw) {
        prevFrameMs = now;
        prevBodyX = bodyMid.x;
        prevBodyY = bodyMid.y;
        rightArm.prevRawX = rw.getPosition().x;
        rightArm.prevRawY = rw.getPosition().y;
        leftArm.prevRawX = lw.getPosition().x;
        leftArm.prevRawY = lw.getPosition().y;
    }

    private void cacheArm(ArmTrack track, ArmFrame frame, long now) {
        track.prevRelX = frame.relX;
        track.prevRelY = frame.relY;
        track.prevDist = frame.dist;
        track.prevAngle = frame.angle;
        track.prevTimeMs = now;
    }

    private static float medianSmooth(Deque<Float> samples, float value) {
        samples.addLast(value);
        while (samples.size() > 5) samples.removeFirst();
        float[] values = new float[samples.size()];
        int i = 0;
        for (float v : samples) values[i++] = v;
        java.util.Arrays.sort(values);
        return values[values.length / 2];
    }

    private static float secondLast(Deque<Float> samples) {
        if (samples.size() < 2) return 0f;
        Float[] values = samples.toArray(new Float[0]);
        return values[values.length - 2];
    }

    private static float jointAngle(PointF a, PointF b, PointF c) {
        float abx = a.x - b.x, aby = a.y - b.y;
        float cbx = c.x - b.x, cby = c.y - b.y;
        float denominator = Math.max(1e-5f, hypot(abx, aby) * hypot(cbx, cby));
        float cosine = clamp((abx * cbx + aby * cby) / denominator, -1f, 1f);
        return (float) Math.toDegrees(Math.acos(cosine));
    }

    private static float wrapAngle(float angle) {
        while (angle > Math.PI) angle -= (float) (Math.PI * 2.0);
        while (angle < -Math.PI) angle += (float) (Math.PI * 2.0);
        return angle;
    }

    private static PoseLandmark lm(Pose pose, int id) { return pose.getPoseLandmark(id); }
    private static PointF midpoint(PointF a, PointF b) { return new PointF((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f); }
    private static float distance(PointF a, PointF b) { return hypot(a.x - b.x, a.y - b.y); }
    private static float distance(float ax, float ay, float bx, float by) { return hypot(ax - bx, ay - by); }
    private static float hypot(float x, float y) { return (float) Math.sqrt(x * x + y * y); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private static float averageLikelihood(PoseLandmark... landmarks) {
        float total = 0f;
        int count = 0;
        for (PoseLandmark landmark : landmarks) {
            if (landmark != null) {
                total += landmark.getInFrameLikelihood();
                count++;
            }
        }
        return count == 0 ? 0f : total / count;
    }
}
