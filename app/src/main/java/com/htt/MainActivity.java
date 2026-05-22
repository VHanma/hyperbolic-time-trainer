package com.htt;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST = 101;
    private static final int PREVIEW_W = 640;
    private static final int PREVIEW_H = 480;

    private TextureView textureView;
    private HudView hudView;
    private Button btnFlip;

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread bgThread;
    private Handler bgHandler;

    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    private String cameraId;

    private StrikeTracker strikeTracker;
    private StrikeDatabase strikeDb;

    private int[] prevArgb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        hudView     = findViewById(R.id.hudView);
        btnFlip     = findViewById(R.id.btnFlip);

        strikeDb = new StrikeDatabase(this);

        strikeTracker = new StrikeTracker(strikeDb, (result, isNewPB) ->
            runOnUiThread(() -> hudView.updateStrike(
                    result,
                    strikeDb.getPersonalBest("speed"),
                    strikeDb.getPersonalBest("power"),
                    strikeDb.getPersonalBest("level"),
                    strikeDb.getTotalStrikes(),
                    strikeDb.getPerfectStrikes(),
                    isNewPB)));

        btnFlip.setOnClickListener(v -> flipCamera());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERM_REQUEST);
        } else {
            startBackgroundThread();
            if (textureView.isAvailable()) openCamera();
            else textureView.setSurfaceTextureListener(surfaceListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) openCamera();
        else textureView.setSurfaceTextureListener(surfaceListener);
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == PERM_REQUEST && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startBackgroundThread();
            if (textureView.isAvailable()) openCamera();
            else textureView.setSurfaceTextureListener(surfaceListener);
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
        }
    }

    private void flipCamera() {
        cameraFacing = (cameraFacing == CameraCharacteristics.LENS_FACING_BACK)
                ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK;
        closeCamera();
        prevArgb = null;
        openCamera();
    }

    private void openCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = null;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == cameraFacing) { cameraId = id; break; }
            }
            if (cameraId == null) return;

            imageReader = ImageReader.newInstance(PREVIEW_W, PREVIEW_H,
                    ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(imgListener, bgHandler);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, stateCallback, bgHandler);
            }
        } catch (CameraAccessException e) { e.printStackTrace(); }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice device) {
            cameraDevice = device;
            createCaptureSession();
        }
        @Override public void onDisconnected(CameraDevice device) { device.close(); cameraDevice = null; }
        @Override public void onError(CameraDevice device, int error) { device.close(); cameraDevice = null; }
    };

    private void createCaptureSession() {
        try {
            SurfaceTexture st = textureView.getSurfaceTexture();
            st.setDefaultBufferSize(PREVIEW_W, PREVIEW_H);
            Surface previewSurface = new Surface(st);
            Surface readerSurface  = imageReader.getSurface();

            CaptureRequest.Builder builder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            builder.addTarget(readerSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, readerSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                builder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                session.setRepeatingRequest(builder.build(), null, bgHandler);
                            } catch (CameraAccessException e) { e.printStackTrace(); }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession session) {}
                    }, bgHandler);
        } catch (CameraAccessException e) { e.printStackTrace(); }
    }

    private void closeCamera() {
        try { if (captureSession != null) { captureSession.close(); captureSession = null; } } catch (Exception ignored) {}
        try { if (cameraDevice  != null) { cameraDevice.close();  cameraDevice  = null; } } catch (Exception ignored) {}
        try { if (imageReader   != null) { imageReader.close();   imageReader   = null; } } catch (Exception ignored) {}
    }

    private final ImageReader.OnImageAvailableListener imgListener = reader -> {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] argb = yuvToArgb(image, w, h);
            if (prevArgb != null) strikeTracker.processFrame(prevArgb, argb, w, h);
            prevArgb = argb;
        } finally {
            image.close();
        }
    };

    private int[] yuvToArgb(Image image, int w, int h) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuf  = planes[0].getBuffer();
        ByteBuffer uBuf  = planes[1].getBuffer();
        ByteBuffer vBuf  = planes[2].getBuffer();

        int yRowStride    = planes[0].getRowStride();
        int uvRowStride   = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        int[] argb = new int[w * h];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int Y = yBuf.get(row * yRowStride + col) & 0xFF;
                int uvRow = (row / 2) * uvRowStride;
                int uvCol = (col / 2) * uvPixelStride;
                int U = uBuf.get(uvRow + uvCol) & 0xFF;
                int V = vBuf.get(uvRow + uvCol) & 0xFF;

                int r = Math.max(0, Math.min(255, (int)(Y + 1.402f   * (V - 128))));
                int g = Math.max(0, Math.min(255, (int)(Y - 0.344136f * (U - 128) - 0.714136f * (V - 128))));
                int b = Math.max(0, Math.min(255, (int)(Y + 1.772f   * (U - 128))));

                argb[row * w + col] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return argb;
    }

    private void startBackgroundThread() {
        if (bgThread != null) return;
        bgThread = new HandlerThread("CamBG");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (bgThread == null) return;
        bgThread.quitSafely();
        try { bgThread.join(); } catch (InterruptedException ignored) {}
        bgThread = null;
        bgHandler = null;
    }

    private final TextureView.SurfaceTextureListener surfaceListener =
            new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { openCamera(); }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
    };
}
