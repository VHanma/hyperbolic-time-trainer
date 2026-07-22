package com.vhanma.freqsight;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Range;
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.ImageAnalysis;
import java.util.concurrent.Executor;

@OptIn(markerClass = ExperimentalCamera2Interop.class)
final class CameraLockController {
    interface Listener { void onCameraMetadata(String text); }

    private volatile Integer lastIso = 400;
    private volatile Long lastExposureNs = 8_000_000L;
    private volatile Float lastFocusDistance = 0f;
    private final Listener listener;

    CameraLockController(Listener listener) { this.listener = listener; }

    void attachCaptureCallback(ImageAnalysis.Builder builder, Executor executor) {
        Camera2Interop.Extender<ImageAnalysis> ext = new Camera2Interop.Extender<>(builder);
        ext.setSessionCaptureCallback(new CameraCaptureSession.CaptureCallback() {
            @Override public void onCaptureCompleted(android.hardware.camera2.CameraCaptureSession session,
                                                       CaptureRequest request, TotalCaptureResult result) {
                Integer iso=result.get(TotalCaptureResult.SENSOR_SENSITIVITY);
                Long exp=result.get(TotalCaptureResult.SENSOR_EXPOSURE_TIME);
                Float focus=result.get(TotalCaptureResult.LENS_FOCUS_DISTANCE);
                if(iso!=null)lastIso=iso;
                if(exp!=null)lastExposureNs=exp;
                if(focus!=null)lastFocusDistance=focus;
                if(listener!=null)listener.onCameraMetadata("ISO "+lastIso+" | shutter "+String.format(java.util.Locale.US,"%.2fms",lastExposureNs/1_000_000.0)+" | focus "+String.format(java.util.Locale.US,"%.2f",lastFocusDistance));
            }
        });
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,new Range<>(30,30));
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
    }

    void lock(Camera camera) {
        try {
            Camera2CameraControl control=Camera2CameraControl.from(camera.getCameraControl());
            CaptureRequestOptions manual=new CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF)
                    .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY,Math.max(50,lastIso))
                    .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME,Math.max(100_000L,lastExposureNs))
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK,true)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF)
                    .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE,Math.max(0f,lastFocusDistance))
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,new Range<>(30,30))
                    .setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                    .build();
            control.setCaptureRequestOptions(manual);
            if(listener!=null)listener.onCameraMetadata("LOCKED: ISO "+lastIso+" | shutter "+String.format(java.util.Locale.US,"%.2fms",lastExposureNs/1_000_000.0)+" | focus/WB/FPS held");
        } catch(Throwable primary) {
            try {
                Camera2CameraControl control=Camera2CameraControl.from(camera.getCameraControl());
                CaptureRequestOptions fallback=new CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK,true)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK,true)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,new Range<>(30,30))
                        .build();
                control.setCaptureRequestOptions(fallback);
                if(listener!=null)listener.onCameraMetadata("LOCK FALLBACK: AE/AWB/FPS held; manual ISO/shutter unsupported");
            } catch(Throwable ignored) {
                if(listener!=null)listener.onCameraMetadata("LOCK REQUEST REJECTED BY DEVICE");
            }
        }
    }
}
