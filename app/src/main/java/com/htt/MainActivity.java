package com.htt;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.*;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST = 101;

    private PreviewView previewView;
    private HudView     hudView;
    private Button      btnFlip;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;

    private PoseDetector poseDetector;
    private StrikeTracker strikeTracker;
    private StrikeDatabase strikeDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        hudView     = findViewById(R.id.hudView);
        btnFlip     = findViewById(R.id.btnFlip);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // ML Kit accurate pose detector (same model family as HITAI)
        AccuratePoseDetectorOptions opts = new AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(opts);

        strikeDb = new StrikeDatabase(this);
        strikeTracker = new StrikeTracker(strikeDb, (result, isNewPB) ->
                runOnUiThread(() -> hudView.onStrike(
                        result,
                        strikeDb.getPersonalBest("speed"),
                        strikeDb.getPersonalBest("power"),
                        strikeDb.getPersonalBest("level"),
                        strikeDb.getTotalStrikes(),
                        strikeDb.getPerfectStrikes(),
                        isNewPB)));

        btnFlip.setOnClickListener(v -> {
            lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                    ? CameraSelector.LENS_FACING_FRONT
                    : CameraSelector.LENS_FACING_BACK;
            if (cameraProvider != null) bindCamera();
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERM_REQUEST);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        cameraProvider.unbindAll();

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing).build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysis.setAnalyzer(cameraExecutor, imageProxy -> {
            @SuppressWarnings("UnsafeOptInUsageError")
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            poseDetector.process(image)
                    .addOnSuccessListener(pose -> strikeTracker.processPose(pose))
                    .addOnCompleteListener(task -> imageProxy.close());
        });

        cameraProvider.bindToLifecycle(this, selector, preview, analysis);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == PERM_REQUEST && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        poseDetector.close();
    }
}
