package com.vhanma.freqsight;

import android.graphics.Bitmap;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DataModels {
    static final class SensorSnapshot {
        long timestampMs;
        float magX, magY, magZ, magMagnitude;
        float accelX, accelY, accelZ, accelMagnitude;
        float gyroX, gyroY, gyroZ, gyroMagnitude;
        float lightLux;
        float vibrationScore;
        boolean stable;
        String anomalySummary = "none";

        String compact() {
            return String.format(Locale.US,
                    "mag %.2fµT | gyro %.3f | accel %.3f | light %.1flux | vibration %.2f | stable %s",
                    magMagnitude, gyroMagnitude, accelMagnitude, lightLux, vibrationScore, stable);
        }
    }

    static final class AudioSnapshot {
        long timestampMs;
        float rms;
        float peakFrequencyHz;
        float peakDb;
        boolean click;
        boolean pulse;
        boolean repeatedPattern;
        String pattern = "";

        String compact() {
            return String.format(Locale.US, "audio rms %.3f | peak %.0f Hz | %.1f dB | %s",
                    rms, peakFrequencyHz, peakDb,
                    click ? "click" : repeatedPattern ? "repeated pattern" : "steady");
        }
    }

    static final class VisualEvent {
        long timestampMs;
        long frameNumber;
        int gridColumn;
        int gridRow;
        float differenceScore;
        float smokeScore;
        float shadowScore;
        float reflectionScore;
        float condensationScore;
        float driftX;
        float driftY;
        float globalShift;
        boolean phoneMovement;
        boolean glyphCandidate;
        Rect glyphBounds;
        String rawOcr = "";
        String repeatedOcr = "";
        float ocrConfidence;
        String symbolPattern = "";
        Bitmap rawFrame;
        Bitmap processedFrame;

        String region() {
            char column = (char) ('A' + Math.max(0, Math.min(4, gridColumn)));
            return column + Integer.toString(Math.max(1, Math.min(5, gridRow + 1)));
        }
    }

    static final class EvidenceEvent {
        long timestampMs;
        long frameNumber;
        String region;
        String rawSource;
        String rawPattern;
        String sensorCorrelation;
        String englishRendering;
        String alternateRendering;
        int confidence;
        String status;
        List<String> reasons = new ArrayList<>();

        String transcriptBlock() {
            return "Timestamp: " + timestampMs + "\n" +
                    "Frame: " + frameNumber + "\n" +
                    "Region: " + region + "\n" +
                    "Raw source: " + rawSource + "\n" +
                    "Raw OCR / pattern: " + rawPattern + "\n" +
                    "Sensor correlation: " + sensorCorrelation + "\n" +
                    "English rendering: " + englishRendering + "\n" +
                    "Confidence: " + confidence + "%\n" +
                    "Alternate: " + alternateRendering + "\n" +
                    "Status: " + status + "\n";
        }
    }
}
