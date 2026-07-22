package com.vhanma.freqsight;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EvidenceEngine {
    private final SymbolDictionary symbols = new SymbolDictionary();
    private final Map<String,Integer> ocrRepetitions = new HashMap<>();
    private final List<String> dictionary = Arrays.asList(
            "HELLO","LIGHT","THREE","EYE","LENS","WITNESS","SOURCE","LAMP","SMOKE","BREATH","SCREEN","CHAMBER","BOUNDARY","MOVE","CLOSER","OPEN","CLOSE","LEFT","RIGHT","UP","DOWN","YES","STOP","LOOK","LISTEN");

    DataModels.EvidenceEvent render(DataModels.VisualEvent v, DataModels.SensorSnapshot s,
                                    DataModels.AudioSnapshot a, boolean beaconContext, String beaconMatch) {
        DataModels.EvidenceEvent out = new DataModels.EvidenceEvent();
        out.timestampMs = v.timestampMs;
        out.frameNumber = v.frameNumber;
        out.region = v.region();

        if (v.phoneMovement || !s.stable) {
            out.rawSource = "camera/sensor contamination";
            out.rawPattern = "phone movement or frame shift";
            out.sensorCorrelation = s.compact();
            out.englishRendering = "NO TRANSLATABLE DATA.";
            out.alternateRendering = "MOTION CONTAMINATION";
            out.confidence = 0;
            out.status = "rejected";
            out.reasons.add("Phone movement exceeded stationary threshold.");
            return out;
        }

        List<String> correlations = new ArrayList<>();
        int sensorSignals = 0;
        if (!"none".equals(s.anomalySummary)) { correlations.add(s.anomalySummary); sensorSignals++; }
        if (a != null && (a.click || a.pulse || a.repeatedPattern)) { correlations.add(a.compact()); sensorSignals++; }
        if (beaconContext && !TextUtils.isEmpty(beaconMatch)) { correlations.add(beaconMatch); sensorSignals++; }

        boolean visibleMark = v.glyphCandidate || !TextUtils.isEmpty(v.rawOcr) || v.differenceScore > 0.16f || v.symbolPattern.contains("change");
        boolean measurablePattern = sensorSignals > 0 || v.smokeScore > 0.16f || v.shadowScore > 0.16f || v.reflectionScore > 0.16f;
        if (!visibleMark && !measurablePattern) return noData(v, s, a);

        String raw = normalizeOcr(v.rawOcr);
        if (!raw.isEmpty()) ocrRepetitions.put(raw, ocrRepetitions.getOrDefault(raw, 0) + 1);
        int repeated = raw.isEmpty() ? 0 : ocrRepetitions.get(raw);
        String corrected = nearestWord(raw);

        out.rawSource = sourceDescription(v);
        out.rawPattern = !raw.isEmpty() ? raw : v.symbolPattern;
        out.sensorCorrelation = correlations.isEmpty() ? "visual evidence only" : String.join("; ", correlations);

        if (!raw.isEmpty() && repeated >= 2) {
            int edit = levenshtein(raw, corrected);
            if (edit == 0) {
                out.englishRendering = raw;
                out.alternateRendering = "Exact repeated OCR mark";
                out.confidence = Math.min(92, 48 + repeated * 12 + sensorSignals * 5);
                out.status = "OCR-supported rendering";
            } else if (edit == 1 && raw.length() >= 3) {
                out.englishRendering = corrected;
                out.alternateRendering = raw + " / " + corrected.toLowerCase(Locale.US) + "-like mark";
                out.confidence = Math.min(78, 38 + repeated * 10 + sensorSignals * 5);
                out.status = "one-edit OCR correction";
            } else {
                out.englishRendering = raw;
                out.alternateRendering = "Uncorrected OCR cluster";
                out.confidence = Math.min(65, 30 + repeated * 8 + sensorSignals * 5);
                out.status = "raw OCR only";
            }
        } else if (v.glyphCandidate || !TextUtils.isEmpty(v.symbolPattern)) {
            out.englishRendering = descriptiveRendering(v, a);
            out.alternateRendering = symbols.userObjectRendering(v);
            out.confidence = Math.min(75, 25 + Math.round(v.differenceScore * 100f / 3f) + sensorSignals * 8 + (v.glyphCandidate ? 12 : 0));
            out.status = "descriptive symbol rendering";
        } else {
            return noData(v, s, a);
        }
        out.reasons.add("English output derived from visible mark or measured pattern.");
        if (sensorSignals > 0) out.reasons.add("Environmental correlation present.");
        if (beaconContext) out.reasons.add("Beacon timing was treated as a stimulus marker, not a speaker.");
        return out;
    }

    private DataModels.EvidenceEvent noData(DataModels.VisualEvent v, DataModels.SensorSnapshot s, DataModels.AudioSnapshot a) {
        DataModels.EvidenceEvent out = new DataModels.EvidenceEvent();
        out.timestampMs=v.timestampMs; out.frameNumber=v.frameNumber; out.region=v.region();
        out.rawSource="none"; out.rawPattern="none";
        out.sensorCorrelation=(a==null?s.compact():s.compact()+"; "+a.compact());
        out.englishRendering="NO TRANSLATABLE DATA.";
        out.alternateRendering="No visible glyph, repeated pattern, decodable pulse, or correlated measurable change.";
        out.confidence=0; out.status="no evidence";
        return out;
    }

    private String sourceDescription(DataModels.VisualEvent v) {
        List<String> s = new ArrayList<>();
        if (v.glyphCandidate) s.add("visual glyph candidate");
        if (v.smokeScore > 0.14f) s.add("smoke-density/motion difference");
        if (v.shadowScore > 0.14f) s.add("shadow-geometry difference");
        if (v.reflectionScore > 0.14f) s.add("reflection/glare difference");
        if (v.condensationScore > 0.14f) s.add("condensation-texture difference");
        if (s.isEmpty()) s.add("frame difference");
        return String.join(" + ", s) + " in region " + v.region();
    }

    private String descriptiveRendering(DataModels.VisualEvent v, DataModels.AudioSnapshot a) {
        List<String> words = new ArrayList<>();
        if (v.shadowScore > 0.18f) words.add("DARK REGION CHANGE");
        if (v.smokeScore > 0.18f) {
            String direction = Math.abs(v.driftX) > Math.abs(v.driftY) ? (v.driftX > 0 ? "RIGHTWARD" : "LEFTWARD") : (v.driftY > 0 ? "DOWNWARD" : "UPWARD");
            words.add(direction + " SMOKE FLOW");
        }
        if (v.reflectionScore > 0.18f) words.add("REFLECTION CHANGE");
        if (v.condensationScore > 0.18f) words.add("SURFACE TEXTURE CHANGE");
        if (v.glyphCandidate) words.add("TEXT-LIKE MARK");
        if (a != null && a.repeatedPattern) words.add(a.pattern.toUpperCase(Locale.US));
        return words.isEmpty() ? "MEASURABLE ENVIRONMENTAL CHANGE" : String.join(" + ", words);
    }

    private String normalizeOcr(String raw) {
        if (raw == null) return "";
        return raw.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "").trim();
    }

    private String nearestWord(String raw) {
        if (raw.isEmpty()) return raw;
        String best = raw; int bestD = Integer.MAX_VALUE;
        for (String w : dictionary) {
            int d = levenshtein(raw, w);
            if (d < bestD) { bestD = d; best = w; }
        }
        return best;
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length()+1], cur = new int[b.length()+1];
        for(int j=0;j<=b.length();j++)prev[j]=j;
        for(int i=1;i<=a.length();i++){
            cur[0]=i;
            for(int j=1;j<=b.length();j++)cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            int[] t=prev;prev=cur;cur=t;
        }
        return prev[b.length()];
    }
}
