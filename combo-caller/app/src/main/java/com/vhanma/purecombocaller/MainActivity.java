package com.vhanma.purecombocaller;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int BG = 0xFF05070B;
    private static final int PANEL = 0xFF0D141C;
    private static final int CYAN = 0xFF62E7FF;
    private static final int MUTED = 0xFF9AA8B6;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<Combo> allCombos = new ArrayList<>();
    private final List<Combo> activeCombos = new ArrayList<>();
    private final Map<String, Integer> categoryCounts = new HashMap<>();

    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean running;
    private boolean paused;
    private boolean immersive;
    private boolean announceRep;
    private boolean expandCodes;
    private boolean preventImmediateRepeat = true;
    private int repeatTarget = 5;
    private int repIndex;
    private int comboIndex;
    private int sequentialIndex;
    private long intervalMs = 5000L;
    private long sessionEndMs;
    private long sessionStartMs;
    private String orderMode = "Random";
    private String selectedCategory = "All combinations";
    private float speechRate = 0.88f;
    private Combo currentCombo;

    private LinearLayout root;
    private TextView comboView;
    private TextView repView;
    private TextView clockView;
    private TextView countView;
    private Button pauseButton;

    private final Runnable caller = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (paused) {
                handler.postDelayed(this, 250L);
                return;
            }
            if (sessionEndMs > 0 && System.currentTimeMillis() >= sessionEndMs) {
                stopSession("ROUND COMPLETE");
                return;
            }
            callNextRepetition();
            handler.postDelayed(this, intervalMs);
        }
    };

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            long elapsed = Math.max(0L, now - sessionStartMs);
            String text;
            if (sessionEndMs > 0) {
                long remain = Math.max(0L, sessionEndMs - now);
                text = formatTime(remain) + " remaining";
            } else {
                text = formatTime(elapsed) + " elapsed";
            }
            if (clockView != null) clockView.setText(text);
            handler.postDelayed(this, 250L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        tts = new TextToSpeech(this, this);
        loadCombos();
        showSetup();
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            tts.setLanguage(Locale.US);
            tts.setSpeechRate(speechRate);
            tts.setPitch(1.0f);
        }
    }

    private void loadCombos() {
        allCombos.clear();
        readAsset("combos_codes.txt", "Code combinations");
        readAsset("combos_named.txt", "Named combinations");
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Combo> clean = new ArrayList<>();
        for (Combo combo : allCombos) {
            String key = combo.raw.trim();
            if (key.isEmpty()) continue;
            if (seen.add(key)) clean.add(combo);
        }
        allCombos.clear();
        allCombos.addAll(clean);
        categoryCounts.clear();
        for (Combo combo : allCombos) {
            categoryCounts.put(combo.category, categoryCounts.getOrDefault(combo.category, 0) + 1);
        }
    }

    private void readAsset(String filename, String source) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(filename)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String raw = line.replaceFirst("^\\s*\\d+\\.\\s*", "").trim();
                if (raw.isEmpty()) continue;
                allCombos.add(new Combo(raw, classify(raw, source)));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Missing " + filename, Toast.LENGTH_LONG).show();
        }
    }

    private String classify(String raw, String source) {
        if (source.startsWith("Code")) return "Coded library";
        String s = raw.toLowerCase(Locale.US);
        if (containsAny(s, "kick", "knee", "teep", "elbow", "hammerfist", "backfist")) return "Muay Thai and kickboxing";
        if (containsAny(s, "slip", "roll", "duck", "shoulder", "pull back", "step back", "side-step", "pivot", "bounce", "catch", "pin jab")) return "Defense and footwork";
        return "Named boxing";
    }

    private boolean containsAny(String s, String... terms) {
        for (String term : terms) if (s.contains(term)) return true;
        return false;
    }

    private void showSetup() {
        leaveImmersive();
        stopInternal();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(24), dp(18), dp(32));
        scroll.addView(panel);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);

        TextView title = label("PURE COMBO CALLER", 32, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, lp(0, 4));
        TextView sub = label("NO CAMERA  •  NO VIDEO  •  JUST COMBINATIONS", 14, CYAN, true);
        sub.setGravity(Gravity.CENTER);
        panel.addView(sub, lp(0, 16));

        countView = label(allCombos.size() + " unique combinations loaded", 16, MUTED, false);
        countView.setGravity(Gravity.CENTER);
        panel.addView(countView, lp(0, 18));

        Spinner category = spinner(new String[]{
                "All combinations",
                "Coded library",
                "Named boxing",
                "Muay Thai and kickboxing",
                "Defense and footwork"
        });
        Spinner order = spinner(new String[]{"Random", "Shuffle deck", "Sequential"});
        Spinner repeats = spinner(new String[]{"1", "2", "3", "5", "10", "20"});
        repeats.setSelection(3);
        Spinner interval = spinner(new String[]{"2 seconds", "3 seconds", "4 seconds", "5 seconds", "6 seconds", "8 seconds", "10 seconds", "12 seconds", "15 seconds"});
        interval.setSelection(3);
        Spinner duration = spinner(new String[]{"1 minute", "3 minutes", "5 minutes", "10 minutes", "20 minutes", "Endless"});
        duration.setSelection(2);

        addField(panel, "LIBRARY", category);
        addField(panel, "ORDER", order);
        addField(panel, "REPEATS PER COMBINATION", repeats);
        addField(panel, "TIME BETWEEN CALLS", interval);
        addField(panel, "ROUND LENGTH", duration);

        TextView rateTitle = label("VOICE SPEED: 88%", 15, Color.WHITE, true);
        panel.addView(rateTitle, lp(8, 0));
        SeekBar rate = new SeekBar(this);
        rate.setMax(100);
        rate.setProgress(48);
        rate.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speechRate = 0.40f + progress * 0.01f;
                rateTitle.setText("VOICE SPEED: " + Math.round(speechRate * 100f) + "%");
                if (ttsReady) tts.setSpeechRate(speechRate);
            }
        });
        panel.addView(rate, lp(0, 6));

        CheckBox rep = check("Announce repetition number", true);
        CheckBox expanded = check("Expand coded notation for clearer speech", true);
        CheckBox avoid = check("Prevent immediate duplicate calls", true);
        panel.addView(rep, lp(2, 0));
        panel.addView(expanded, lp(2, 0));
        panel.addView(avoid, lp(2, 12));

        TextView preview = label("Tap TEST VOICE to hear the current speech style.", 14, MUTED, false);
        preview.setGravity(Gravity.CENTER);
        panel.addView(preview, lp(6, 8));

        LinearLayout testRow = horizontal();
        Button test = button("TEST VOICE");
        Button count = button("SHOW COUNT");
        testRow.addView(test, weight());
        testRow.addView(count, weight());
        panel.addView(testRow, lp(4, 8));

        Button start = button("START CALLER");
        start.setTextSize(20);
        panel.addView(start, lp(10, 8));

        test.setOnClickListener(v -> speak(expanded.isChecked() ? expandCode("1-/L/-3b-4") : "1, L, 3 b, 4"));
        count.setOnClickListener(v -> {
            String categoryName = String.valueOf(category.getSelectedItem());
            int total = countFor(categoryName);
            preview.setText(categoryName + ": " + total + " combinations");
        });
        start.setOnClickListener(v -> {
            selectedCategory = String.valueOf(category.getSelectedItem());
            orderMode = String.valueOf(order.getSelectedItem());
            repeatTarget = Integer.parseInt(String.valueOf(repeats.getSelectedItem()));
            intervalMs = parseSeconds(String.valueOf(interval.getSelectedItem())) * 1000L;
            announceRep = rep.isChecked();
            expandCodes = expanded.isChecked();
            preventImmediateRepeat = avoid.isChecked();
            long durationMs = parseDuration(String.valueOf(duration.getSelectedItem()));
            prepareActiveList();
            if (activeCombos.isEmpty()) {
                Toast.makeText(this, "That library is empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            startSession(durationMs);
        });
    }

    private int countFor(String category) {
        if (category.equals("All combinations")) return allCombos.size();
        return categoryCounts.getOrDefault(category, 0);
    }

    private void prepareActiveList() {
        activeCombos.clear();
        for (Combo combo : allCombos) {
            if (selectedCategory.equals("All combinations") || combo.category.equals(selectedCategory)) {
                activeCombos.add(combo);
            }
        }
        if (orderMode.equals("Shuffle deck")) Collections.shuffle(activeCombos, random);
        sequentialIndex = 0;
    }

    private void startSession(long durationMs) {
        running = true;
        paused = false;
        currentCombo = null;
        repIndex = repeatTarget;
        comboIndex = 0;
        sessionStartMs = System.currentTimeMillis();
        sessionEndMs = durationMs > 0 ? sessionStartMs + durationMs : 0L;
        enterImmersive();
        showCallerScreen();
        handler.removeCallbacks(caller);
        handler.removeCallbacks(clock);
        handler.post(caller);
        handler.post(clock);
    }

    private void showCallerScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER_HORIZONTAL);
        screen.setPadding(dp(18), dp(22), dp(18), dp(16));
        screen.setBackgroundColor(BG);
        setContentView(screen);

        TextView title = label("PURE COMBO CALLER", 16, CYAN, true);
        title.setGravity(Gravity.CENTER);
        screen.addView(title, lp(0, 10));

        clockView = label("00:00", 15, MUTED, true);
        clockView.setGravity(Gravity.CENTER);
        screen.addView(clockView, lp(0, 12));

        comboView = label("GET READY", 34, Color.WHITE, true);
        comboView.setGravity(Gravity.CENTER);
        comboView.setPadding(dp(16), dp(28), dp(16), dp(28));
        comboView.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams comboLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        comboLp.setMargins(0, dp(8), 0, dp(12));
        screen.addView(comboView, comboLp);

        repView = label("", 20, CYAN, true);
        repView.setGravity(Gravity.CENTER);
        screen.addView(repView, lp(0, 12));

        LinearLayout controls = horizontal();
        Button previous = button("PREV");
        pauseButton = button("PAUSE");
        Button next = button("NEXT");
        controls.addView(previous, weight());
        controls.addView(pauseButton, weight());
        controls.addView(next, weight());
        screen.addView(controls, lp(0, 8));

        Button stop = button("STOP");
        screen.addView(stop, lp(0, 0));

        previous.setOnClickListener(v -> replayCurrent());
        pauseButton.setOnClickListener(v -> togglePause());
        next.setOnClickListener(v -> skipCombo());
        stop.setOnClickListener(v -> stopSession("STOPPED"));
    }

    private void callNextRepetition() {
        if (currentCombo == null || repIndex >= repeatTarget) {
            currentCombo = chooseCombo();
            repIndex = 0;
            comboIndex++;
        }
        repIndex++;
        comboView.setText(currentCombo.raw);
        repView.setText("REP " + repIndex + " / " + repeatTarget + "   •   COMBO " + comboIndex);
        String spoken = currentCombo.category.equals("Coded library") && expandCodes
                ? expandCode(currentCombo.raw)
                : normalizeNamed(currentCombo.raw);
        if (announceRep) spoken = "Rep " + repIndex + ". " + spoken;
        speak(spoken);
    }

    private Combo chooseCombo() {
        if (orderMode.equals("Sequential") || orderMode.equals("Shuffle deck")) {
            if (sequentialIndex >= activeCombos.size()) {
                sequentialIndex = 0;
                if (orderMode.equals("Shuffle deck")) Collections.shuffle(activeCombos, random);
            }
            return activeCombos.get(sequentialIndex++);
        }
        if (activeCombos.size() == 1) return activeCombos.get(0);
        Combo choice;
        int guard = 0;
        do {
            choice = activeCombos.get(random.nextInt(activeCombos.size()));
            guard++;
        } while (preventImmediateRepeat && currentCombo != null && choice.raw.equals(currentCombo.raw) && guard < 20);
        return choice;
    }

    private void replayCurrent() {
        if (currentCombo == null) return;
        String spoken = currentCombo.category.equals("Coded library") && expandCodes
                ? expandCode(currentCombo.raw)
                : normalizeNamed(currentCombo.raw);
        speak("Repeat. " + spoken);
    }

    private void skipCombo() {
        repIndex = repeatTarget;
        handler.removeCallbacks(caller);
        callNextRepetition();
        handler.postDelayed(caller, intervalMs);
    }

    private void togglePause() {
        paused = !paused;
        pauseButton.setText(paused ? "RESUME" : "PAUSE");
        if (paused) {
            if (ttsReady) tts.stop();
            repView.setText("PAUSED");
        } else {
            repView.setText("RESUMED");
        }
    }

    private void stopSession(String message) {
        stopInternal();
        leaveImmersive();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        showSetup();
    }

    private void stopInternal() {
        running = false;
        paused = false;
        handler.removeCallbacksAndMessages(null);
        if (ttsReady) tts.stop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private String expandCode(String raw) {
        String s = raw;
        s = s.replace("pivote", "pivot");
        s = s.replace("/L/", " left ");
        s = s.replace("/R/", " right ");
        s = s.replace("<R>", " right ");
        s = s.replace("{", "").replace("}", "");
        s = s.replace("ccw", " counter clockwise ");
        s = s.replace("-", " , ");
        s = s.replace("/", " ");
        s = expandLetterGroups(s);
        s = s.replaceAll("\\s+,\\s+", ", ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private String expandLetterGroups(String input) {
        Matcher matcher = Pattern.compile("(?i)(\\d+)?([a-z]+)").matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String number = matcher.group(1) == null ? "" : matcher.group(1) + " ";
            String letters = matcher.group(2).toLowerCase(Locale.US);
            StringBuilder spoken = new StringBuilder(number);
            for (int i = 0; i < letters.length(); i++) {
                char c = letters.charAt(i);
                if (c == 'b') spoken.append("bee ");
                else if (c == 'p') spoken.append("pee ");
                else if (c == 'f') spoken.append("eff ");
                else if (c == 't') spoken.append("tee ");
                else if (c == 's') spoken.append("ess ");
                else if (c == 'l') spoken.append("left ");
                else if (c == 'r') spoken.append("right ");
                else spoken.append(c).append(' ');
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(spoken.toString().trim()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String normalizeNamed(String raw) {
        return raw.replace('—', ',').replace(";", ",").replaceAll("\\s+", " ").trim();
    }

    private void speak(String text) {
        if (!ttsReady) {
            Toast.makeText(this, "Voice engine is still loading.", Toast.LENGTH_SHORT).show();
            return;
        }
        tts.setSpeechRate(speechRate);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "combo-" + System.nanoTime());
    }

    private void enterImmersive() {
        immersive = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void leaveImmersive() {
        immersive = false;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override public void onBackPressed() {
        if (running || immersive) stopSession("STOPPED");
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        stopInternal();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private long parseSeconds(String text) {
        Matcher m = Pattern.compile("(\\d+)").matcher(text);
        return m.find() ? Long.parseLong(m.group(1)) : 5L;
    }

    private long parseDuration(String text) {
        if (text.toLowerCase(Locale.US).contains("endless")) return 0L;
        Matcher m = Pattern.compile("(\\d+)").matcher(text);
        return m.find() ? Long.parseLong(m.group(1)) * 60_000L : 300_000L;
    }

    private String formatTime(long ms) {
        long sec = ms / 1000L;
        return String.format(Locale.US, "%02d:%02d", sec / 60L, sec % 60L);
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                view.setTextSize(16);
                view.setPadding(dp(12), dp(12), dp(12), dp(12));
                return view;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setBackgroundColor(PANEL);
        return spinner;
    }

    private void addField(LinearLayout panel, String title, View field) {
        TextView label = label(title, 13, CYAN, true);
        panel.addView(label, lp(8, 3));
        panel.addView(field, lp(0, 8));
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setTextColor(Color.WHITE);
        box.setTextSize(16);
        box.setChecked(checked);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
        return box;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundColor(0xFF144E65);
        button.setAllCaps(false);
        button.setPadding(dp(8), dp(14), dp(8), dp(14));
        return button;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private LinearLayout.LayoutParams lp(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(top), 0, dp(bottom));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private static final class Combo {
        final String raw;
        final String category;
        Combo(String raw, String category) {
            this.raw = raw;
            this.category = category;
        }
    }
}
