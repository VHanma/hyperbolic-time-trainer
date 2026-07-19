package com.htt;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Two independent TTS streams: a clear coach and a quieter reinforcement voice. */
public final class SpeechEngine {
    private TextToSpeech coach;
    private TextToSpeech whisper;
    private final AtomicBoolean coachReady = new AtomicBoolean(false);
    private final AtomicBoolean whisperReady = new AtomicBoolean(false);
    private float coachVolume = 1.0f;
    private float whisperVolume = 0.14f;

    public SpeechEngine(Context context) {
        Context app = context.getApplicationContext();
        coach = new TextToSpeech(app, status -> {
            if (status == TextToSpeech.SUCCESS && coach != null) {
                coach.setLanguage(Locale.US);
                coach.setSpeechRate(0.98f);
                coach.setPitch(0.96f);
                coachReady.set(true);
            }
        });
        whisper = new TextToSpeech(app, status -> {
            if (status == TextToSpeech.SUCCESS && whisper != null) {
                whisper.setLanguage(Locale.US);
                whisper.setSpeechRate(0.82f);
                whisper.setPitch(0.82f);
                whisperReady.set(true);
            }
        });
    }

    public void setCoachVolume(float value) { coachVolume = clamp(value); }
    public void setWhisperVolume(float value) { whisperVolume = clamp(value); }

    public void speakCoach(String text) {
        if (!coachReady.get() || coach == null || text == null || text.trim().isEmpty()) return;
        Bundle b = new Bundle();
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, coachVolume);
        coach.speak(text, TextToSpeech.QUEUE_FLUSH, b, "coach_" + System.nanoTime());
    }

    public void speakWhisper(String text) {
        if (!whisperReady.get() || whisper == null || text == null || text.trim().isEmpty()) return;
        Bundle b = new Bundle();
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, whisperVolume);
        whisper.speak(text, TextToSpeech.QUEUE_FLUSH, b, "whisper_" + System.nanoTime());
    }

    public void stop() {
        if (coach != null) coach.stop();
        if (whisper != null) whisper.stop();
    }

    public void shutdown() {
        stop();
        if (coach != null) coach.shutdown();
        if (whisper != null) whisper.shutdown();
        coach = null;
        whisper = null;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
