package com.htt;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * IRON MIND V4: Hyperbolic Fight Chamber.
 * Offline combo coach, passive embedding lab, stroboscopic occlusion and camera strike analysis.
 */
public final class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION = 401;
    private static final int MODE_HOME = 0;
    private static final int MODE_PASSIVE = 1;
    private static final int MODE_COMBO = 2;
    private static final int MODE_VISION = 3;
    private static final int MODE_FUSION = 4;
    private static final int MODE_REPORT = 5;
    private static final int COMBO_REPETITIONS = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private ComboBank bank;
    private SpeechEngine speech;
    private ToneEngine tones;
    private TorchPulseEngine torch;
    private SharedPreferences prefs;
    private StrobeView strobeView;
    private int currentMode = MODE_HOME;

    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;
    private HudView hudView;
    private PoseDetector poseDetector;
    private StrikeTracker strikeTracker;
    private StrikeDatabase strikeDb;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;

    private long sessionStartedMs;
    private long sessionEndsMs;
    private int combosCalled;
    private int combosCompleted;
    private ComboBank.Combo currentCombo;
    private int expectedIndex;
    private int comboRepeatIndex;
    private boolean currentRepetitionCompleted;
    private TextView sessionClock;
    private TextView comboText;
    private String activeCategory = ComboBank.CAT_MIXED;
    private long callIntervalMs = 5000;
    private boolean whisperEnabled = true;
    private boolean fullSourceAffirmations;
    private boolean fusionRunning;
    private String lastTrackingStatus = "";
    private String lastLiveReadout = "";

    private final String[] visualProfiles = {
            "Off",
            "Breathing pulse 0.1Hz",
            "Temporal 4Hz low contrast",
            "Occlusion 10Hz / 70% open",
            "Occlusion 8Hz / 60% open",
            "Occlusion 7Hz / 55% open",
            "Occlusion 5Hz / 50% open",
            "Peripheral 8Hz small field"
    };

    private final String[] audioProfiles = {
            "Off",
            "10 Hz binaural focus",
            "15 Hz binaural active",
            "6 Hz binaural imagery",
            "40 Hz amplitude lab",
            "432 Hz + 10 Hz modulation",
            "528 Hz + 6 Hz modulation",
            "7.83 Hz isochronic",
            "111 Hz + 4 Hz modulation",
            "136.1 Hz + 7.83 Hz modulation",
            "369 Hz + 10 Hz modulation"
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("iron_mind_v4", MODE_PRIVATE);
        bank = new ComboBank(this);
        speech = new SpeechEngine(this);
        tones = new ToneEngine();
        torch = new TorchPulseEngine(this);
        strikeDb = new StrikeDatabase(this);
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05070B);
        setContentView(root);
        showHome();
    }

    private void showHome() {
        stopEverything();
        currentMode = MODE_HOME;
        root.removeAllViews();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ScrollView scroll = new ScrollView(this);
        LinearLayout column = vertical(18);
        column.setPadding(dp(18), dp(24), dp(18), dp(34));
        scroll.addView(column);
        root.addView(scroll, match());

        TextView title = text("IRON MIND V4", 34, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        column.addView(title, fullWrap(0));
        TextView subtitle = text("HYPERBOLIC FIGHT CHAMBER", 20, 0xFF5DE5FF, true);
        subtitle.setGravity(Gravity.CENTER);
        column.addView(subtitle, fullWrap(4));
        TextView stats = text(bank.comboCount() + " combo paths  |  " + bank.affirmationCount() + " message forms\nOffline. Personal. No accounts.", 15, 0xFFB5C0CE, false);
        stats.setGravity(Gravity.CENTER);
        column.addView(stats, fullWrap(14));

        column.addView(modeButton("PASSIVE CHAMBER", "Affirmations, visual pulses, audio carriers, fringe laboratory", () -> showPassive()), fullWrap(12));
        column.addView(modeButton("NEMESIS COMBO COACH", "Every combo repeats five times with quiet matched affirmations", () -> showComboCoach()), fullWrap(10));
        column.addView(modeButton("LIVE VISION ANALYSIS", "Guard-calibrated strike states, speed range, modeled force and readable tracking status", () -> showCamera(false)), fullWrap(10));
        column.addView(modeButton("FUSION CHAMBER", "Five-repeat combo caller + quiet cues + strobe + live analysis", () -> showCamera(true)), fullWrap(10));
        column.addView(modeButton("CALIBRATION", "Body weight, shoulder width, stance and personal scaling", this::showCalibration), fullWrap(10));
        column.addView(modeButton("RESEARCH + SESSION REPORT", "Measured density, model limits, scientific and experimental layers", this::showReport), fullWrap(10));

        TextView footer = text("The chamber increases useful training density. It does not create literal extra time, directly measure impact force, or install unpracticed techniques.", 13, 0xFF7F8A96, false);
        footer.setGravity(Gravity.CENTER);
        column.addView(footer, fullWrap(22));
    }

    private void showPassive() {
        stopEverything();
        currentMode = MODE_PASSIVE;
        root.removeAllViews();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        strobeView = new StrobeView(this);
        root.addView(strobeView, match());
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = vertical(10);
        panel.setPadding(dp(18), dp(22), dp(18), dp(36));
        scroll.addView(panel);
        root.addView(scroll, match());
        addHeader(panel, "PASSIVE CHAMBER", "Use while stationary. Clear instruction first; quiet and experimental layers remain secondary.");

        Spinner category = spinner(bank.affirmationCategories().toArray(new String[0]));
        Spinner visual = spinner(visualProfiles);
        Spinner audio = spinner(audioProfiles);
        Spinner duration = spinner(new String[]{"5 minutes", "10 minutes", "20 minutes", "30 minutes"});
        Spinner torchSpinner = spinner(new String[]{"Torch off", "Torch pulse 1 Hz", "Torch pulse 2 Hz", "Torch pulse 4 Hz"});
        CheckBox audible = check("Audible affirmation layer", true);
        CheckBox quiet = check("Lower-volume reinforcement layer", true);
        CheckBox source = check("Include longer source affirmations", false);
        addLabeled(panel, "MESSAGE BANK", category);
        addLabeled(panel, "SCREEN PROFILE", visual);
        addLabeled(panel, "AUDIO PROFILE", audio);
        addLabeled(panel, "REAR TORCH LAB", torchSpinner);
        addLabeled(panel, "SESSION", duration);
        panel.addView(audible, fullWrap(4));
        panel.addView(quiet, fullWrap(2));
        panel.addView(source, fullWrap(2));

        TextView cue = text("READY", 28, Color.WHITE, true);
        cue.setGravity(Gravity.CENTER);
        cue.setBackground(round(0xCC0B1118, 16, 0xFF31485C));
        cue.setPadding(dp(12), dp(22), dp(12), dp(22));
        panel.addView(cue, fullWrap(14));
        sessionClock = text("00:00", 18, 0xFF74E5FF, true);
        sessionClock.setGravity(Gravity.CENTER);
        panel.addView(sessionClock, fullWrap(8));

        LinearLayout buttons = horizontal(8);
        Button start = actionButton("START");
        Button stop = actionButton("STOP");
        buttons.addView(start, weight()); buttons.addView(stop, weight());
        panel.addView(buttons, fullWrap(14));
        panel.addView(actionButtonWith("BACK", this::showHome), fullWrap(8));

        start.setOnClickListener(v -> {
            stopSessionOnly();
            fullSourceAffirmations = source.isChecked();
            int mins = parseLeadingInt((String) duration.getSelectedItem(), 10);
            sessionStartedMs = System.currentTimeMillis();
            sessionEndsMs = sessionStartedMs + mins * 60_000L;
            strobeView.startProfile((String) visual.getSelectedItem());
            tones.start((String) audio.getSelectedItem(), 0.09f);
            startTorchFromSelection((String) torchSpinner.getSelectedItem());
            passiveLoop((String) category.getSelectedItem(), audible.isChecked(), quiet.isChecked(), cue);
            clockLoop();
        });
        stop.setOnClickListener(v -> {
            finishSession("passive");
            cue.setText("STOPPED");
        });
    }

    private void passiveLoop(String category, boolean audible, boolean quiet, TextView cue) {
        if (sessionEndsMs <= System.currentTimeMillis() || currentMode != MODE_PASSIVE) {
            finishSession("passive");
            cue.setText("SESSION COMPLETE");
            return;
        }
        String message = bank.randomAffirmation(category, fullSourceAffirmations);
        cue.setText(message);
        if (audible) speech.speakCoach(message);
        if (quiet) handler.postDelayed(() -> speech.speakWhisper(message), 280L);
        handler.postDelayed(() -> passiveLoop(category, audible, quiet, cue), 8500L);
    }

    private void showComboCoach() {
        stopEverything();
        currentMode = MODE_COMBO;
        root.removeAllViews();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        strobeView = new StrobeView(this);
        root.addView(strobeView, match());

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = vertical(10);
        panel.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(panel);
        root.addView(scroll, match());
        addHeader(panel, "NEMESIS COMBO COACH", "Every selected combo repeats five consecutive times before the next one is drawn.");

        Spinner category = spinner(bank.categories().toArray(new String[0]));
        category.setSelection(bank.categories().indexOf(ComboBank.CAT_MIXED));
        Spinner duration = spinner(new String[]{"1 minute", "3 minutes", "5 minutes", "10 minutes"});
        Spinner pace = spinner(new String[]{"8 seconds", "6.5 seconds", "5 seconds", "3.5 seconds"});
        Spinner visual = spinner(visualProfiles);
        Spinner audio = spinner(audioProfiles);
        CheckBox whisper = check("Lower-volume matched affirmations beneath every repetition", true);
        addLabeled(panel, "COMBO BANK", category);
        addLabeled(panel, "ROUND", duration);
        addLabeled(panel, "CALL INTERVAL", pace);
        addLabeled(panel, "VISUAL PROFILE", visual);
        addLabeled(panel, "AUDIO PROFILE", audio);
        panel.addView(whisper, fullWrap(4));

        comboText = text("Press START", 25, Color.WHITE, true);
        comboText.setGravity(Gravity.CENTER);
        comboText.setBackground(round(0xDD0B1118, 18, 0xFF3A566B));
        comboText.setPadding(dp(14), dp(24), dp(14), dp(24));
        panel.addView(comboText, fullWrap(14));
        sessionClock = text("00:00", 18, 0xFF68E9FF, true);
        sessionClock.setGravity(Gravity.CENTER);
        panel.addView(sessionClock, fullWrap(8));

        LinearLayout controls = horizontal(8);
        Button start = actionButton("START");
        Button done = actionButton("DONE");
        Button stop = actionButton("STOP");
        controls.addView(start, weight()); controls.addView(done, weight()); controls.addView(stop, weight());
        panel.addView(controls, fullWrap(14));
        panel.addView(actionButtonWith("BACK", this::showHome), fullWrap(8));

        start.setOnClickListener(v -> {
            stopSessionOnly();
            activeCategory = (String) category.getSelectedItem();
            whisperEnabled = whisper.isChecked();
            int mins = parseLeadingInt((String) duration.getSelectedItem(), 3);
            callIntervalMs = (long) (parseLeadingDouble((String) pace.getSelectedItem(), 5) * 1000);
            sessionStartedMs = System.currentTimeMillis();
            sessionEndsMs = sessionStartedMs + mins * 60_000L;
            combosCalled = combosCompleted = 0;
            currentCombo = null;
            comboRepeatIndex = 0;
            currentRepetitionCompleted = false;
            strobeView.startProfile((String) visual.getSelectedItem());
            tones.start((String) audio.getSelectedItem(), 0.10f);
            callComboLoop(false);
            clockLoop();
        });
        done.setOnClickListener(v -> {
            if (sessionEndsMs > System.currentTimeMillis() && currentCombo != null && !currentRepetitionCompleted) {
                currentRepetitionCompleted = true;
                combosCompleted++;
                done.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                if (comboText != null) {
                    comboText.setText(currentCombo.spoken + "\n\nREP " + comboRepeatIndex + " / " + COMBO_REPETITIONS + " COMPLETE\n" + densitySummary());
                }
            }
        });
        stop.setOnClickListener(v -> {
            finishSession("combo");
            comboText.setText("Round stopped.");
        });
    }

    private void showCamera(boolean fusion) {
        stopEverything();
        currentMode = fusion ? MODE_FUSION : MODE_VISION;
        fusionRunning = fusion;
        root.removeAllViews();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, match());
        strobeView = new StrobeView(this);
        root.addView(strobeView, match());
        hudView = new HudView(this);
        root.addView(hudView, match());

        LinearLayout top = horizontal(8);
        top.setPadding(dp(10), dp(10), dp(10), dp(10));
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0x99000000);
        Button back = smallButton("BACK");
        Button flip = smallButton("FLIP");
        Button recalibrate = smallButton("RECAL");
        Button body = smallButton("BODY");
        top.addView(back); top.addView(flip); top.addView(recalibrate); top.addView(body);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        root.addView(top, topLp);
        back.setOnClickListener(v -> showHome());
        flip.setOnClickListener(v -> {
            lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
            bindCamera();
        });
        recalibrate.setOnClickListener(v -> {
            if (strikeTracker != null) strikeTracker.resetCalibration();
            if (hudView != null) {
                hudView.setTrackingStatus("CALIBRATION RESET");
                hudView.setLiveReadout("Hold your normal guard still for about two seconds");
            }
            Toast.makeText(this, "Live calibration reset. Hold guard still.", Toast.LENGTH_SHORT).show();
        });
        body.setOnClickListener(v -> showCalibration());

        if (fusion) addFusionControls();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    private void addFusionControls() {
        LinearLayout bottom = vertical(7);
        bottom.setPadding(dp(10), dp(8), dp(10), dp(12));
        bottom.setBackgroundColor(0xB6000000);
        Spinner category = spinner(bank.categories().toArray(new String[0]));
        category.setSelection(bank.categories().indexOf(ComboBank.CAT_MIXED));
        Spinner visual = spinner(new String[]{"Off", "Occlusion 10Hz / 70% open", "Occlusion 8Hz / 60% open", "Occlusion 7Hz / 55% open", "Occlusion 5Hz / 50% open", "Temporal 4Hz low contrast"});
        Spinner audio = spinner(audioProfiles);
        bottom.addView(category, fullWrap(0));
        bottom.addView(visual, fullWrap(2));
        bottom.addView(audio, fullWrap(2));
        LinearLayout row = horizontal(8);
        Button start = smallButton("START FUSION");
        Button stop = smallButton("STOP");
        row.addView(start, weight()); row.addView(stop, weight());
        bottom.addView(row, fullWrap(4));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(bottom, lp);

        start.setOnClickListener(v -> {
            activeCategory = (String) category.getSelectedItem();
            whisperEnabled = true;
            callIntervalMs = 6500L;
            sessionStartedMs = System.currentTimeMillis();
            sessionEndsMs = sessionStartedMs + 3 * 60_000L;
            combosCalled = combosCompleted = 0;
            currentCombo = null;
            comboRepeatIndex = 0;
            currentRepetitionCompleted = false;
            strobeView.startProfile((String) visual.getSelectedItem());
            tones.start((String) audio.getSelectedItem(), 0.08f);
            callComboLoop(true);
        });
        stop.setOnClickListener(v -> {
            finishSession("fusion");
            if (hudView != null) hudView.setComboPrompt("Fusion stopped", "");
        });
    }

    private void callComboLoop(boolean fusion) {
        if (sessionEndsMs <= System.currentTimeMillis() || (fusion && currentMode != MODE_FUSION) || (!fusion && currentMode != MODE_COMBO)) {
            finishSession(fusion ? "fusion" : "combo");
            if (comboText != null) comboText.setText("ROUND COMPLETE");
            if (hudView != null) hudView.setComboPrompt("ROUND COMPLETE", densitySummary());
            return;
        }
        if (currentCombo == null || comboRepeatIndex >= COMBO_REPETITIONS) {
            currentCombo = bank.randomCombo(activeCategory);
            comboRepeatIndex = 0;
        }
        comboRepeatIndex++;
        expectedIndex = 0;
        currentRepetitionCompleted = false;
        combosCalled++;
        String call = currentCombo.spoken;
        String repeatLabel = "REP " + comboRepeatIndex + " / " + COMBO_REPETITIONS;
        speech.speakCoach("Repeat " + comboRepeatIndex + " of " + COMBO_REPETITIONS + ". " + call);
        if (whisperEnabled) {
            handler.postDelayed(() -> {
                if (sessionEndsMs > System.currentTimeMillis()) speech.speakWhisper(bank.matchedAffirmation(currentCombo));
            }, 220L);
        }
        if (fusion && hudView != null) {
            hudView.setComboPrompt(call, repeatLabel + "  |  " + expectedProgress());
        } else if (comboText != null) {
            comboText.setText(call + "\n\n" + repeatLabel + "\n" + densitySummary());
        }
        handler.postDelayed(() -> callComboLoop(fusion), callIntervalMs);
    }

    private void onDetectedStrike(StrikeTracker.StrikeResult r) {
        if (currentMode == MODE_FUSION && currentCombo != null && !currentCombo.expectedPunches.isEmpty()) {
            if (!currentRepetitionCompleted) {
                String expected = currentCombo.expectedPunches.get(Math.min(expectedIndex, currentCombo.expectedPunches.size() - 1));
                if (strikeMatches(expected, r.punchType)) {
                    expectedIndex++;
                    if (expectedIndex >= currentCombo.expectedPunches.size()) {
                        currentRepetitionCompleted = true;
                        combosCompleted++;
                        expectedIndex = currentCombo.expectedPunches.size();
                    }
                }
            }
            hudView.setComboPrompt(currentCombo.spoken, "REP " + comboRepeatIndex + " / " + COMBO_REPETITIONS + "  |  " + expectedProgress());
        }
    }

    private String expectedProgress() {
        if (currentCombo == null || currentCombo.expectedPunches.isEmpty()) return densitySummary();
        return "Detected " + expectedIndex + " / " + currentCombo.expectedPunches.size() + " punches  |  " + densitySummary();
    }

    private boolean strikeMatches(String expected, String actual) {
        if (expected.equals(actual)) return true;
        return (expected.equals("CROSS") && actual.equals("OVERHAND")) || (expected.equals("OVERHAND") && actual.equals("CROSS"));
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                Toast.makeText(this, "Camera could not start: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null || previewView == null) return;
        cameraProvider.unbindAll();
        CameraSelector selector;
        try {
            selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
            if (!cameraProvider.hasCamera(selector)) {
                lensFacing = CameraSelector.LENS_FACING_BACK;
                selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
            }
        } catch (Exception e) {
            return;
        }
        int targetRotation = previewView.getDisplay() == null ? 0 : previewView.getDisplay().getRotation();
        Preview preview = new Preview.Builder()
                .setTargetRotation(targetRotation)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetRotation(targetRotation)
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        strikeTracker = new StrikeTracker(strikeDb, prefs, (result, isNewPb) -> {
            runOnUiThread(() -> {
                hudView.onStrike(result,
                        strikeDb.getPersonalBest("speed"),
                        strikeDb.getPersonalBest("power"),
                        strikeDb.getPersonalBest("level"),
                        strikeDb.getTotalStrikes(),
                        strikeDb.getPerfectStrikes(),
                        isNewPb);
                onDetectedStrike(result);
            });
        });
        strikeTracker.resetCalibration();
        lastTrackingStatus = "";
        lastLiveReadout = "";
        analysis.setAnalyzer(cameraExecutor, imageProxy -> analyzeFrame(imageProxy));
        try {
            cameraProvider.bindToLifecycle(this, selector, preview, analysis);
            hudView.setTrackingStatus("STARTING FAST POSE TRACKER");
            hudView.setLiveReadout("Stand at a 30-45 degree angle and hold your normal guard still");
        } catch (Exception e) {
            hudView.setTrackingStatus("CAMERA BIND FAILED");
        }
    }

    private void analyzeFrame(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        long timestampMs = imageProxy.getImageInfo().getTimestamp() / 1_000_000L;
        poseDetector.process(image)
                .addOnSuccessListener(cameraExecutor, pose -> {
                    if (strikeTracker != null) {
                        strikeTracker.processPose(pose, timestampMs);
                        String status = strikeTracker.getLiveStatus();
                        String readout = strikeTracker.getLiveReadout();
                        if (!status.equals(lastTrackingStatus) || !readout.equals(lastLiveReadout)) {
                            lastTrackingStatus = status;
                            lastLiveReadout = readout;
                            runOnUiThread(() -> {
                                if (hudView != null) {
                                    hudView.setTrackingStatus(status);
                                    hudView.setLiveReadout(readout);
                                }
                            });
                        }
                    }
                })
                .addOnFailureListener(cameraExecutor, e -> {
                    if (hudView != null) runOnUiThread(() -> {
                        hudView.setTrackingStatus("TRACKING ERROR");
                        hudView.setLiveReadout("The pose model lost the frame. Improve light and reframe.");
                    });
                })
                .addOnCompleteListener(cameraExecutor, task -> imageProxy.close());
    }

    private void showCalibration() {
        final LinearLayout form = vertical(10);
        form.setPadding(dp(18), dp(8), dp(18), dp(4));
        EditText weight = numberInput(String.valueOf(prefs.getFloat("body_weight_lb", 150f)), "Body weight in pounds");
        EditText shoulder = decimalInput(String.valueOf(prefs.getFloat("shoulder_width_in", 17f)), "Shoulder width in inches");
        Spinner stance = spinner(new String[]{"Orthodox", "Southpaw"});
        stance.setSelection(prefs.getBoolean("orthodox", true) ? 0 : 1);
        form.addView(text("Body weight (lb)", 15, Color.WHITE, true)); form.addView(weight, fullWrap(0));
        form.addView(text("Shoulder width (in)", 15, Color.WHITE, true)); form.addView(shoulder, fullWrap(0));
        form.addView(text("Primary stance", 15, Color.WHITE, true)); form.addView(stance, fullWrap(0));
        TextView note = text("Current defaults also assume 5'7 height, 71-inch wingspan, 31-inch left chest-to-knuckle reach and 26-inch right shoulder-to-knuckle reach. Shoulder width anchors the live physical scale. Each live session separately learns your exact guard and camera angle.", 13, 0xFFB8C1CC, false);
        form.addView(note, fullWrap(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Personal calibration")
                .setView(form)
                .setNegativeButton("CANCEL", null)
                .create();
        form.addView(actionButtonWith("SAVE", () -> {
            try {
                float w = Float.parseFloat(weight.getText().toString());
                float s = Float.parseFloat(shoulder.getText().toString());
                prefs.edit().putFloat("body_weight_lb", w).putFloat("shoulder_width_in", s)
                        .putBoolean("orthodox", stance.getSelectedItemPosition() == 0).apply();
                if (strikeTracker != null) strikeTracker.resetCalibration();
                Toast.makeText(this, "Body settings saved. Live guard calibration reset.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Enter valid numbers", Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
    }

    private void showReport() {
        stopEverything();
        currentMode = MODE_REPORT;
        root.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = vertical(10);
        panel.setPadding(dp(18), dp(22), dp(18), dp(36));
        scroll.addView(panel);
        root.addView(scroll, match());
        addHeader(panel, "RESEARCH + REPORT", "The app separates measured functions, models, and experimental layers.");

        String report = "LAST SESSION\n" +
                "Type: " + prefs.getString("last_type", "none") + "\n" +
                "Real minutes: " + String.format(Locale.US, "%.1f", prefs.getFloat("last_minutes", 0f)) + "\n" +
                "Combo repetitions called: " + prefs.getInt("last_called", 0) + "\n" +
                "Repetitions completed: " + prefs.getInt("last_completed", 0) + "\n" +
                "Training density: " + String.format(Locale.US, "%.2f", prefs.getFloat("last_density", 0f)) + " completed repetitions/min\n\n" +
                "LIVE CAMERA MODEL\n" +
                "Live Forge learns the guard first, tracks each arm through guard, extension, peak and return, and rejects incomplete motion. It uses the faster streaming pose model and a lower analysis resolution to improve processed frame rate. Speed is displayed as a sampling-aware range. Force remains a broad hypothetical impact model, not a force plate.\n\n" +
                "VISUAL LAYERS\n" +
                "Occlusion profiles alternate transparent and black frames to train prediction under incomplete visual information. Temporal and peripheral modes are low-contrast screen effects. Rear torch timing is approximate because phone hardware controls differ.\n\n" +
                "AUDIO + FRINGE LAB\n" +
                "Binaural, isochronic, 432, 528, 7.83, 111, 136.1 and 369-themed carriers are optional sound constructions. The app makes no automatic biological claim for a number. Their value can be judged by your session data, focus and retention.\n\n" +
                "SUBLIMINAL LAYER\n" +
                "Quiet messages reinforce already understood mechanics and tactics. The clear coach remains dominant during active work. Complex skill still requires physical practice.";
        TextView body = text(report, 15, 0xFFD4DCE6, false);
        body.setLineSpacing(0, 1.18f);
        body.setBackground(round(0xFF0B1017, 15, 0xFF273A4B));
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.addView(body, fullWrap(12));
        panel.addView(actionButtonWith("BACK", this::showHome), fullWrap(14));
    }

    private void startTorchFromSelection(String selection) {
        if (selection == null || selection.startsWith("Torch off")) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission is needed for torch pulses", Toast.LENGTH_SHORT).show();
            return;
        }
        double hz = parseLeadingDouble(selection.replace("Torch pulse ", ""), 1);
        torch.start(hz);
    }

    private void clockLoop() {
        if (sessionClock == null || sessionEndsMs <= 0) return;
        long left = Math.max(0, sessionEndsMs - System.currentTimeMillis());
        sessionClock.setText(String.format(Locale.US, "%02d:%02d  |  %s",
                left / 60_000, (left / 1000) % 60, densitySummary()));
        if (left > 0) handler.postDelayed(this::clockLoop, 500);
    }

    private String densitySummary() {
        if (sessionStartedMs <= 0) return "Density 0.00/min";
        float minutes = Math.max(1f / 60f, (System.currentTimeMillis() - sessionStartedMs) / 60_000f);
        return String.format(Locale.US, "Density %.2f/min", combosCompleted / minutes);
    }

    private void finishSession(String type) {
        if (sessionStartedMs > 0) {
            float minutes = Math.max(0.01f, (System.currentTimeMillis() - sessionStartedMs) / 60_000f);
            float density = combosCompleted / minutes;
            prefs.edit().putString("last_type", type).putFloat("last_minutes", minutes)
                    .putInt("last_called", combosCalled).putInt("last_completed", combosCompleted)
                    .putFloat("last_density", density).apply();
        }
        stopSessionOnly();
    }

    private void stopSessionOnly() {
        handler.removeCallbacksAndMessages(null);
        speech.stop();
        tones.stop();
        torch.stop();
        if (strobeView != null) strobeView.stop();
        sessionEndsMs = 0;
        sessionStartedMs = 0;
        currentCombo = null;
        expectedIndex = 0;
        comboRepeatIndex = 0;
        currentRepetitionCompleted = false;
    }

    private void stopEverything() {
        stopSessionOnly();
        fusionRunning = false;
        if (cameraProvider != null) cameraProvider.unbindAll();
        previewView = null;
        hudView = null;
        strikeTracker = null;
    }

    @Override public void onBackPressed() {
        if (currentMode == MODE_HOME) super.onBackPressed();
        else showHome();
    }

    @Override protected void onDestroy() {
        stopEverything();
        speech.shutdown();
        poseDetector.close();
        cameraExecutor.shutdown();
        strikeDb.close();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (currentMode == MODE_VISION || currentMode == MODE_FUSION) startCamera();
        } else if (requestCode == CAMERA_PERMISSION) {
            Toast.makeText(this, "Camera mode requires camera permission", Toast.LENGTH_LONG).show();
            showHome();
        }
    }

    private void addHeader(LinearLayout parent, String heading, String sub) {
        TextView title = text(heading, 27, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        parent.addView(title, fullWrap(0));
        TextView subtitle = text(sub, 14, 0xFF98A7B6, false);
        subtitle.setGravity(Gravity.CENTER);
        parent.addView(subtitle, fullWrap(5));
    }

    private Button modeButton(String title, String sub, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title + "\n" + sub);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(15), dp(12), dp(15));
        b.setBackground(round(0xFF101721, 15, 0xFF2F485D));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(0xFF0E4660, 12, 0xFF50D9FF));
        return b;
    }
    private Button actionButtonWith(String label, Runnable action) {
        Button b = actionButton(label); b.setOnClickListener(v -> action.run()); return b;
    }
    private Button smallButton(String label) {
        Button b = actionButton(label);
        b.setTextSize(11);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        return b;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(Color.WHITE); v.setTextSize(14); v.setPadding(dp(10), dp(9), dp(10), dp(9));
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(Color.BLACK); v.setTextSize(14); return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setBackground(round(0xFF151D27, 10, 0xFF3A5368));
        return s;
    }

    private void addLabeled(LinearLayout parent, String labelText, View control) {
        parent.addView(text(labelText, 14, 0xFFD7E0EA, true), fullWrap(8));
        parent.addView(control, fullWrap(1));
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(text); c.setTextColor(Color.WHITE); c.setTextSize(15); c.setChecked(checked);
        return c;
    }

    private TextView text(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private LinearLayout vertical(int spacing) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE);
        return l;
    }

    private LinearLayout horizontal(int spacing) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private SeekBar seek(int progress, int min, int max) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        if (android.os.Build.VERSION.SDK_INT >= 26) s.setMin(min);
        s.setProgress(progress);
        return s;
    }

    private SeekBar.OnSeekBarChangeListener simpleSeek(IntConsumer consumer) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { consumer.accept(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private EditText numberInput(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(0xFF7F8994);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }
    private EditText decimalInput(String value, String hint) { return numberInput(value, hint); }

    private GradientDrawable round(int fill, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radiusDp)); d.setStroke(dp(1), stroke);
        return d;
    }

    private FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT); }
    private LinearLayout.LayoutParams fullWrap(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp); return p;
    }
    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(3), 0, dp(3), 0); return p;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static int parseLeadingInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim().split(" ")[0]); } catch (Exception e) { return fallback; }
    }
    private static double parseLeadingDouble(String s, double fallback) {
        try { return Double.parseDouble(s.trim().split(" ")[0]); } catch (Exception e) { return fallback; }
    }

    private interface IntConsumer { void accept(int value); }
}
