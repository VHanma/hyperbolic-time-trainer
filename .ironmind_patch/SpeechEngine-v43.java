package com.htt;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Three independent speech layers: clear natural coach, whisper reinforcement, and ultra-low texture. */
public final class SpeechEngine {
    private TextToSpeech coach, whisper, texture;
    private final AtomicBoolean coachReady = new AtomicBoolean(false);
    private final AtomicBoolean whisperReady = new AtomicBoolean(false);
    private final AtomicBoolean textureReady = new AtomicBoolean(false);
    private float coachVolume = 0.85f, whisperVolume = 0.18f, textureVolume = 0.05f;

    public SpeechEngine(Context context) {
        Context app = context.getApplicationContext();
        coach = new TextToSpeech(app, status -> {
            if (status == TextToSpeech.SUCCESS && coach != null) {
                coach.setLanguage(Locale.US); coach.setSpeechRate(0.96f); coach.setPitch(0.94f); coachReady.set(true);
            }
        });
        whisper = new TextToSpeech(app, status -> {
            if (status == TextToSpeech.SUCCESS && whisper != null) {
                whisper.setLanguage(Locale.US); whisper.setSpeechRate(0.86f); whisper.setPitch(0.80f); whisperReady.set(true);
            }
        });
        texture = new TextToSpeech(app, status -> {
            if (status == TextToSpeech.SUCCESS && texture != null) {
                texture.setLanguage(Locale.US); texture.setSpeechRate(1.28f); texture.setPitch(0.72f); textureReady.set(true);
            }
        });
    }

    public void setCoachVolume(float value) { coachVolume = clamp(value); }
    public void setWhisperVolume(float value) { whisperVolume = clamp(value); }
    public void setTextureVolume(float value) { textureVolume = clamp(value); }

    public void speakCoach(String text) { speak(coach, coachReady, text, coachVolume, TextToSpeech.QUEUE_FLUSH, "coach"); }

    public void speakCoachNatural(String text, int variant) {
        if (coach != null) {
            float[] rates = {0.90f, 0.96f, 1.02f, 0.93f, 1.00f};
            float[] pitches = {0.92f, 0.97f, 0.89f, 1.01f, 0.94f};
            int i = Math.abs(variant) % rates.length;
            coach.setSpeechRate(rates[i]); coach.setPitch(pitches[i]);
        }
        speakCoach(text);
    }

    public void speakWhisper(String text) { speak(whisper, whisperReady, text, whisperVolume, TextToSpeech.QUEUE_ADD, "whisper"); }

    public void speakTexture(String text, boolean fringe) {
        if (texture != null) {
            texture.setSpeechRate(fringe ? 1.55f : 1.25f);
            texture.setPitch(fringe ? 0.62f : 0.75f);
        }
        speak(texture, textureReady, text, textureVolume, TextToSpeech.QUEUE_ADD, "texture");
    }

    private void speak(TextToSpeech tts, AtomicBoolean ready, String text, float volume, int queue, String id) {
        if (!ready.get() || tts == null || text == null || text.trim().isEmpty()) return;
        Bundle b = new Bundle();
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, id.equals("whisper") ? -0.35f : (id.equals("texture") ? 0.35f : 0f));
        tts.speak(text, queue, b, id + "_" + System.nanoTime());
    }

    public void stop() { if (coach != null) coach.stop(); if (whisper != null) whisper.stop(); if (texture != null) texture.stop(); }
    public void shutdown() { stop(); if (coach != null) coach.shutdown(); if (whisper != null) whisper.shutdown(); if (texture != null) texture.shutdown(); coach = whisper = texture = null; }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
