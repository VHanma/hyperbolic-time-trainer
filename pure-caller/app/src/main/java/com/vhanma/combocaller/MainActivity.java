package com.vhanma.combocaller;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int BG = Color.rgb(8, 9, 12), PANEL = Color.rgb(25, 28, 35), TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(175, 180, 190), RED = Color.rgb(229, 57, 53), CYAN = Color.rgb(80, 210, 255);
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rng = new Random();
    private final List<Combo> all = new ArrayList<>(), bank = new ArrayList<>();
    private TextToSpeech tts;
    private boolean ready, running, paused, unlimited;
    private int index, rep, targetRepeats = 5, intervalMs = 4000;
    private long endsAt, pausedLeft;
    private LinearLayout settings, session;
    private Spinner order, group, repeats, round, voice;
    private SeekBar interval, speed;
    private CheckBox numbers, readyWord, vibration;
    private TextView comboView, spokenView, repView, timerView, idView;
    private Button pause;
    private Combo current;

    private final Runnable timerTask = new Runnable() {
        @Override public void run() {
            if (!running || paused) return;
            if (unlimited) timerView.setText("∞");
            else {
                long left = Math.max(0, endsAt - SystemClock.elapsedRealtime());
                timerView.setText(time(left));
                if (left == 0) { finishRound(); return; }
            }
            h.postDelayed(this, 200);
        }
    };

    private final Runnable callTask = new Runnable() {
        @Override public void run() {
            if (!running || paused) return;
            if (!unlimited && SystemClock.elapsedRealtime() >= endsAt) { finishRound(); return; }
            if (current == null || rep >= targetRepeats) { current = next(); rep = 0; targetRepeats = repeatCount(); }
            rep++;
            call(current, rep, targetRepeats);
            h.postDelayed(this, intervalMs);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        load();
        build();
        tts = new TextToSpeech(this, this);
    }

    private void load() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open("combos.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int p = line.indexOf('\t');
                if (p > 0) all.add(new Combo(Integer.parseInt(line.substring(0, p)), line.substring(p + 1)));
            }
        } catch (Exception e) { Toast.makeText(this, "Catalog failed to load", Toast.LENGTH_LONG).show(); }
    }

    private void build() {
        LinearLayout root = col(); root.setBackgroundColor(BG);
        settings = settings(); session = session(); session.setVisibility(View.GONE);
        root.addView(settings, new LinearLayout.LayoutParams(-1, -1));
        root.addView(session, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private LinearLayout settings() {
        ScrollView sc = new ScrollView(this); sc.setFillViewport(true);
        LinearLayout box = col(); box.setPadding(dp(18), dp(18), dp(18), dp(36)); sc.addView(box);
        box.addView(txt("HANMA COMBO CALLER", 29, TEXT, true));
        TextView sub = txt("PURE 898 • OFFLINE • NO CAMERA", 13, CYAN, true); sub.setPadding(0, 4, 0, 18); box.addView(sub);
        box.addView(label("ORDER")); order = spin("Random", "Sequential", "Shuffle once"); box.addView(order);
        box.addView(label("BANK")); group = spin("All 898", "Coded 1–400", "Named 401–898"); box.addView(group);
        box.addView(label("REPEAT EACH COMBO")); repeats = spin("1", "2", "3", "5", "10", "20", "Random 2–5"); repeats.setSelection(3); box.addView(repeats);
        box.addView(label("ROUND")); round = spin("1 minute", "2 minutes", "3 minutes", "5 minutes", "10 minutes", "20 minutes", "Unlimited"); round.setSelection(2); box.addView(round);
        box.addView(label("VOICE")); voice = spin("Expanded movement names", "Exact coded call"); box.addView(voice);
        TextView il = label("TIME BETWEEN CALLS: 4.0 SEC"); box.addView(il);
        interval = seek(21, 5); interval.setOnSeekBarChangeListener(listener(p -> { intervalMs = 1500 + p * 500; il.setText(String.format(Locale.US, "TIME BETWEEN CALLS: %.1f SEC", intervalMs / 1000f)); })); box.addView(interval);
        TextView sl = label("VOICE SPEED: 1.00×"); box.addView(sl);
        speed = seek(20, 10); speed.setOnSeekBarChangeListener(listener(p -> { float v = .5f + p * .05f; sl.setText(String.format(Locale.US, "VOICE SPEED: %.2f×", v)); if (ready) tts.setSpeechRate(v); })); box.addView(speed);
        numbers = check("Announce combo number", false); readyWord = check("Say Ready before a new combo", true); vibration = check("Vibrate on each call", true);
        box.addView(numbers); box.addView(readyWord); box.addView(vibration);
        Button test = button("TEST VOICE", PANEL); test.setOnClickListener(v -> speak("Ready. Combination one. Jab, cross, lead hook.")); box.addView(test);
        Button start = button("START CALLER", RED); start.setOnClickListener(v -> start()); box.addView(start);
        TextView verified = txt("CATALOG VERIFIED: " + all.size() + " COMBINATIONS", 12, MUTED, true); verified.setGravity(Gravity.CENTER); verified.setPadding(0, 18, 0, 0); box.addView(verified);
        LinearLayout wrapper = col(); wrapper.addView(sc, new LinearLayout.LayoutParams(-1, -1)); return wrapper;
    }

    private LinearLayout session() {
        LinearLayout box = col(); box.setPadding(dp(14), dp(18), dp(14), dp(14)); box.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout top = row(); timerView = txt("03:00", 22, CYAN, true); idView = txt("#0", 17, MUTED, true); top.addView(timerView, new LinearLayout.LayoutParams(0, -2, 1)); top.addView(idView); box.addView(top, new LinearLayout.LayoutParams(-1, -2));
        comboView = txt("READY", 42, TEXT, true); comboView.setGravity(Gravity.CENTER); box.addView(comboView, new LinearLayout.LayoutParams(-1, 0, 1));
        spokenView = txt("", 18, MUTED, false); spokenView.setGravity(Gravity.CENTER); box.addView(spokenView);
        repView = txt("REP 0 / 0", 24, CYAN, true); repView.setGravity(Gravity.CENTER); repView.setPadding(0, 14, 0, 16); box.addView(repView);
        LinearLayout a = row(); a.addView(small("REPEAT", v -> repeatNow()), new LinearLayout.LayoutParams(0, dp(58), 1)); a.addView(small("NEXT", v -> nextNow()), new LinearLayout.LayoutParams(0, dp(58), 1)); box.addView(a, new LinearLayout.LayoutParams(-1, dp(58)));
        LinearLayout b = row(); pause = small("PAUSE", v -> pause()); b.addView(pause, new LinearLayout.LayoutParams(0, dp(58), 1)); b.addView(small("STOP", v -> stop()), new LinearLayout.LayoutParams(0, dp(58), 1)); box.addView(b, new LinearLayout.LayoutParams(-1, dp(58)));
        return box;
    }

    private void start() {
        if (all.size() != 898) { Toast.makeText(this, "Catalog check failed", Toast.LENGTH_LONG).show(); return; }
        bank.clear(); int g = group.getSelectedItemPosition();
        for (Combo c : all) if (g == 0 || (g == 1 && c.id <= 400) || (g == 2 && c.id > 400)) bank.add(c);
        if (order.getSelectedItemPosition() == 2) Collections.shuffle(bank, rng);
        index = 0; rep = 0; current = null; running = true; paused = false; intervalMs = 1500 + interval.getProgress() * 500;
        int[] mins = {1,2,3,5,10,20,0}; int m = mins[round.getSelectedItemPosition()]; unlimited = m == 0; endsAt = unlimited ? Long.MAX_VALUE : SystemClock.elapsedRealtime() + m * 60000L;
        settings.setVisibility(View.GONE); session.setVisibility(View.VISIBLE); immersive();
        h.post(callTask); h.post(timerTask);
    }

    private Combo next() {
        if (order.getSelectedItemPosition() == 0) return bank.get(rng.nextInt(bank.size()));
        if (index >= bank.size()) index = 0;
        return bank.get(index++);
    }

    private int repeatCount() {
        int[] v = {1,2,3,5,10,20}; int p = repeats.getSelectedItemPosition(); return p < 6 ? v[p] : 2 + rng.nextInt(4);
    }

    private void call(Combo c, int r, int max) {
        String words = ComboSpeech.speak(c.text, c.id, voice.getSelectedItemPosition() == 0);
        String prefix = (r == 1 && readyWord.isChecked() ? "Ready. " : "") + (r == 1 && numbers.isChecked() ? "Combination " + c.id + ". " : "");
        comboView.setText(c.text); spokenView.setText(words); repView.setText("REP " + r + " / " + max); idView.setText("#" + c.id + " • " + bank.size());
        speak(prefix + words); if (vibration.isChecked()) vibrate();
    }

    private void nextNow() { if (!running) return; rep = targetRepeats; h.removeCallbacks(callTask); callTask.run(); }
    private void repeatNow() { if (current != null) call(current, Math.max(1, rep), targetRepeats); }
    private void pause() {
        if (!running) return;
        if (!paused) { paused = true; pausedLeft = unlimited ? Long.MAX_VALUE : Math.max(0, endsAt - SystemClock.elapsedRealtime()); h.removeCallbacks(callTask); h.removeCallbacks(timerTask); tts.stop(); pause.setText("RESUME"); }
        else { paused = false; if (!unlimited) endsAt = SystemClock.elapsedRealtime() + pausedLeft; pause.setText("PAUSE"); h.post(callTask); h.post(timerTask); }
    }
    private void finishRound() { running = false; h.removeCallbacksAndMessages(null); speak("Round complete"); comboView.setText("ROUND COMPLETE"); repView.setText("PRESS STOP"); }
    private void stop() { running = false; paused = false; h.removeCallbacksAndMessages(null); if (tts != null) tts.stop(); session.setVisibility(View.GONE); settings.setVisibility(View.VISIBLE); visibleBars(); }

    private void speak(String s) { if (!ready) return; tts.setSpeechRate(.5f + speed.getProgress() * .05f); tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "call" + SystemClock.elapsedRealtime()); }
    @Override public void onInit(int status) { ready = status == TextToSpeech.SUCCESS; if (ready) { tts.setLanguage(Locale.US); tts.setPitch(.9f); } }
    private void vibrate() { Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE); if (v == null) return; if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(45, 100)); else v.vibrate(45); }
    private void immersive() { getWindow().getDecorView().setSystemUiVisibility(5894); }
    private void visibleBars() { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE); }
    @Override public void onBackPressed() { if (session.getVisibility() == View.VISIBLE) stop(); else super.onBackPressed(); }
    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }

    private LinearLayout col() { LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); return x; }
    private LinearLayout row() { LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.HORIZONTAL); return x; }
    private TextView txt(String s, int size, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private TextView label(String s) { TextView v = txt(s, 13, MUTED, true); v.setPadding(0, 16, 0, 7); return v; }
    private Spinner spin(String... items) { Spinner s = new Spinner(this); ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items); s.setAdapter(a); s.setBackground(roundRect(PANEL)); s.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52))); return s; }
    private CheckBox check(String s, boolean on) { CheckBox c = new CheckBox(this); c.setText(s); c.setTextColor(TEXT); c.setTextSize(16); c.setChecked(on); c.setButtonTintList(ColorStateList.valueOf(CYAN)); return c; }
    private SeekBar seek(int max, int value) { SeekBar s = new SeekBar(this); s.setMax(max); s.setProgress(value); s.setProgressTintList(ColorStateList.valueOf(RED)); s.setThumbTintList(ColorStateList.valueOf(RED)); return s; }
    private Button button(String s, int color) { Button b = new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(17); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(roundRect(color)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58)); p.topMargin = dp(12); b.setLayoutParams(p); return b; }
    private Button small(String s, View.OnClickListener l) { Button b = button(s, PANEL); b.setOnClickListener(l); b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(58), 1)); return b; }
    private GradientDrawable roundRect(int color) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(14)); g.setStroke(dp(1), color == RED ? RED : Color.rgb(50,55,65)); return g; }
    private SeekBar.OnSeekBarChangeListener listener(IntChange c) { return new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s,int p,boolean f){c.go(p);} public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }; }
    private String time(long ms) { long s = ms / 1000; return String.format(Locale.US, "%02d:%02d", s / 60, s % 60); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private interface IntChange { void go(int n); }
    private static final class Combo { final int id; final String text; Combo(int id,String text){this.id=id;this.text=text;} }
}
