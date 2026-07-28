from pathlib import Path

root = Path(__file__).resolve().parents[1] / "hanma-caller-x"
java = root / "app/src/main/java/com/vhanma/hanmacallerx/MainActivity.java"
gradle = root / "app/build.gradle"
text = java.read_text()

old = '''    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        totalCalls = prefs.getInt("total_calls", 0);
        totalRounds = prefs.getInt("total_rounds", 0);
        loadAllDrills();
        showSetup();
        handler.post(this::initTtsSafely);
    }
'''
new = '''    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showBootstrapScreen();
        handler.post(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                totalCalls = prefs.getInt("total_calls", 0);
                totalRounds = prefs.getInt("total_rounds", 0);
                loadAllDrills();
                if (allDrills.isEmpty()) throw new IllegalStateException("No drills loaded");
                showSetup();
            } catch (Throwable error) {
                showStartupFailure(error);
            }
        });
    }

    private void showBootstrapScreen() {
        LinearLayout boot = vertical(BG);
        boot.setGravity(Gravity.CENTER);
        TextView title = label("HANMA COMBO CALLER X", 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        boot.addView(title, lp(0, 12));
        TextView loading = label("Loading the arsenal safely…", 16, CYAN, false);
        loading.setGravity(Gravity.CENTER);
        boot.addView(loading, lp(0, 0));
        setContentView(boot);
    }

    private void showStartupFailure(Throwable error) {
        stopInternal();
        LinearLayout failure = vertical(BG);
        failure.setPadding(dp(20), dp(30), dp(20), dp(30));
        TextView title = label("STARTUP RECOVERY", 26, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        failure.addView(title, lp(0, 12));
        String message = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        TextView details = label(message, 15, GOLD, false);
        details.setTextIsSelectable(true);
        failure.addView(details, lp(0, 16));
        Button retry = button("RETRY DATABASE LOAD");
        failure.addView(retry, lp(0, 8));
        retry.setOnClickListener(v -> {
            showBootstrapScreen();
            handler.post(() -> {
                try {
                    loadAllDrills();
                    if (allDrills.isEmpty()) throw new IllegalStateException("No drills loaded");
                    showSetup();
                } catch (Throwable retryError) {
                    showStartupFailure(retryError);
                }
            });
        });
        setContentView(failure);
    }
'''
if old not in text:
    raise SystemExit("onCreate block not found")
text = text.replace(old, new)
text = text.replace("    private Drill currentDrill;\n", "    private Drill currentDrill;\n    private String pendingSpeech;\n")

old_finish = '''            if (!ttsReady) Toast.makeText(this, "English voice data is missing or unsupported.", Toast.LENGTH_LONG).show();
        } catch (Throwable error) {
'''
new_finish = '''            if (!ttsReady) {
                Toast.makeText(this, "English voice data is missing or unsupported.", Toast.LENGTH_LONG).show();
            } else if (pendingSpeech != null) {
                String queued = pendingSpeech;
                pendingSpeech = null;
                speak(queued);
            }
        } catch (Throwable error) {
'''
if old_finish not in text:
    raise SystemExit("TTS completion block not found")
text = text.replace(old_finish, new_finish)

old_speak = '''    private void speak(String text) {
        if (!ttsReady || tts == null) {
            Toast.makeText(this, "Voice engine is still loading or unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        tts.setSpeechRate(speechRate);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hanma-" + System.nanoTime());
    }
'''
new_speak = '''    private void speak(String text) {
        if (tts == null) {
            pendingSpeech = text;
            initTtsSafely();
            Toast.makeText(this, "Starting the offline voice engine…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ttsReady) {
            pendingSpeech = text;
            Toast.makeText(this, "Voice engine is still loading or unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            tts.setSpeechRate(speechRate);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hanma-" + System.nanoTime());
        } catch (Throwable error) {
            ttsReady = false;
            Toast.makeText(this, "Voice engine failed, but the caller is still running.", Toast.LENGTH_LONG).show();
        }
    }
'''
if old_speak not in text:
    raise SystemExit("speak block not found")
text = text.replace(old_speak, new_speak)
java.write_text(text)

g = gradle.read_text()
g = g.replace("versionCode 21", "versionCode 22")
g = g.replace("versionName '2.0.1-Hotfix'", "versionName '2.0.2-StagedStartup'")
gradle.write_text(g)
print("v2.0.2 staged startup patch applied")
