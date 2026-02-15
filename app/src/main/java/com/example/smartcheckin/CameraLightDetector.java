package com.example.smartcheckin;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.util.Collections;

public class CameraLightDetector {

    public interface LightCallback {
        void onDarkDetected();
        void onLightDetected();
    }

    private static final int DARK_THRESHOLD = 45;
    private static final long DARK_CONFIRM_MS = 3000;
    private static final long FAST_DARK_CONFIRM_MS = 500;
    private static final long TORCH_AUTO_OFF_MS = 120_000;
    private static final long CAMERA_REOPEN_DELAY_MS = 3000;
    private static final long POST_OFF_GRACE_PERIOD_MS = 1500;

    private final Context context;
    private final CameraManager cameraManager;
    private final LightCallback callback;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private boolean isDark = false;
    private long darkStartTime = 0;
    private boolean torchOn = false;
    private long lastTorchOffTime = 0;
    private boolean fastReCheckAfterTimeout = false;

    private String cameraId;

    public CameraLightDetector(Context context, LightCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public void start() {
        if (cameraManager == null) return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e("CameraLight", "Camera permission not granted");
            return;
        }

        if (backgroundThread != null && backgroundThread.isAlive()) return;

        backgroundThread = new HandlerThread("CameraLightThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        try {
            cameraId = cameraManager.getCameraIdList()[0];
            CameraCharacteristics chars = cameraManager.getCameraCharacteristics(cameraId);
            Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (hasFlash == null || !hasFlash) {
                Log.w("CameraLight", "No torch support");
                return;
            }
        } catch (Exception e) {
            Log.e("CameraLight", "Camera init failed", e);
            return;
        }

        createImageReader();
        openCameraToMonitor();
    }

    private void createImageReader() {
        imageReader = ImageReader.newInstance(352, 288, ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image == null) return;

            try {
                int brightness = calculateBrightness(image);
                long now = System.currentTimeMillis();

                long confirmMs = fastReCheckAfterTimeout ? FAST_DARK_CONFIRM_MS : DARK_CONFIRM_MS;

                Log.d("CameraLight", "Brightness = " + brightness +
                        " | confirmMs = " + confirmMs +
                        " | fastMode = " + fastReCheckAfterTimeout +
                        " | darkStartTime = " + darkStartTime +
                        " | isDark = " + isDark +
                        " | torchOn = " + torchOn);

                if (brightness < DARK_THRESHOLD) {
                    if (darkStartTime == 0) {
                        darkStartTime = now;
                        Log.d("CameraLight", "Dark timer STARTED at " + now);
                    }

                    if (!isDark && now - darkStartTime >= confirmMs) {
                        isDark = true;
                        callback.onDarkDetected();
                        turnTorch(true);
                        fastReCheckAfterTimeout = false;
                        Log.d("CameraLight", "Dark confirmed → torch ON after " + (now - darkStartTime) + " ms");
                    } else if (isDark) {
                        Log.d("CameraLight", "Already in dark state - skipping ON");
                    }
                } else {
                    boolean ignoreDueToTorch = torchOn || (now - lastTorchOffTime < POST_OFF_GRACE_PERIOD_MS);

                    if (!ignoreDueToTorch && darkStartTime != 0) {
                        Log.d("CameraLight", "Dark timer RESET - real light detected");
                        darkStartTime = 0;
                    } else if (ignoreDueToTorch && darkStartTime != 0) {
                        Log.d("CameraLight", "Brightness spike IGNORED (torch/reflection/grace)");
                    }

                    if (isDark) {
                        isDark = false;
                        callback.onLightDetected();
                        turnTorch(false);
                    }
                }
            } finally {
                image.close();
            }
        }, backgroundHandler);
    }

    private void openCameraToMonitor() {
        if (torchOn) {
            Log.d("CameraLight", "Skipped reopen - torch ON");
            return;
        }

        // Important: reset state when reopening after OFF
        darkStartTime = 0;
        isDark = false;   // ← force reset here
        Log.d("CameraLight", "State reset: isDark=false, darkStartTime=0 on reopen");

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e("CameraLight", "Permission missing");
            return;
        }

        try {
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            Log.d("CameraLight", "Camera reopened for detection");
        } catch (Exception e) {
            Log.e("CameraLight", "Reopen failed", e);
            backgroundHandler.postDelayed(this::openCameraToMonitor, 2000);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            setupPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e("CameraLight", "Camera error " + error);
            closeCamera();
        }
    };

    private void setupPreview() {
        if (cameraDevice == null || imageReader == null) return;

        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(imageReader.getSurface());
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);

            cameraDevice.createCaptureSession(Collections.singletonList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler);
                            } catch (Exception e) {
                                Log.e("CameraLight", "Repeating failed", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e("CameraLight", "Config failed");
                        }
                    }, backgroundHandler);
        } catch (Exception e) {
            Log.e("CameraLight", "Preview failed", e);
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void turnTorch(boolean enable) {
        if (enable == torchOn) return;

        closeCamera();

        try {
            cameraManager.setTorchMode(cameraId, enable);
            torchOn = enable;
            if (enable) {
                Log.d("CameraLight", "Torch turned ON");
            } else {
                lastTorchOffTime = System.currentTimeMillis();
                isDark = false;  // ← also reset here when we explicitly turn off
                Log.d("CameraLight", "Torch turned OFF - isDark forced to false");
            }
        } catch (Exception e) {
            Log.e("CameraLight", "Torch failed", e);
            torchOn = false;
            isDark = false;
        }

        if (enable) {
            backgroundHandler.postDelayed(() -> {
                if (torchOn) {
                    Log.d("CameraLight", "Timeout → OFF");
                    turnTorch(false);
                    fastReCheckAfterTimeout = true;
                    backgroundHandler.postDelayed(() -> {
                        Log.d("CameraLight", "Reopening camera - fast re-check active");
                        openCameraToMonitor();
                    }, CAMERA_REOPEN_DELAY_MS);
                }
            }, TORCH_AUTO_OFF_MS);
        } else {
            darkStartTime = 0;
            backgroundHandler.postDelayed(() -> {
                Log.d("CameraLight", "Reopening camera after OFF");
                openCameraToMonitor();
            }, 800);
        }
    }

    private int calculateBrightness(Image image) {
        Image.Plane yPlane = image.getPlanes()[0];
        ByteBuffer buffer = yPlane.getBuffer();
        int rowStride = yPlane.getRowStride();
        int pixelStride = yPlane.getPixelStride();

        long sum = 0;
        int count = 0;
        int width = image.getWidth();
        int height = image.getHeight();

        for (int row = 0; row < height; row += 8) {
            int offset = row * rowStride;
            for (int col = 0; col < width; col += 8) {
                int pos = offset + col * pixelStride;
                if (pos >= buffer.limit()) break;
                sum += buffer.get(pos) & 0xFF;
                count++;
            }
        }
        return count == 0 ? 128 : (int) (sum / count);
    }

    public void stop() {
        turnTorch(false);
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        closeCamera();

        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join(500);
            } catch (InterruptedException ignored) {}
            backgroundThread = null;
            backgroundHandler = null;
        }
    }
}