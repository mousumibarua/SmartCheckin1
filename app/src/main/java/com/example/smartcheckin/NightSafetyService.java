package com.example.smartcheckin;

import static android.content.Context.CAMERA_SERVICE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;

public class NightSafetyService extends Service {

    private CameraManager cameraManager;
    private String cameraId;

    @Override
    public void onCreate() {
        super.onCreate();
        PackageManager pm = getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            Log.e("TORCH", "Device has no flashlight feature");
            stopSelf();   // 🔴 IMPORTANT: stop service cleanly
            return;
        }


        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        cameraId = findTorchCameraId();

        startForeground(
                101,
                buildNotification("Night safety active")
        );

        // Simple heuristic loop
        new Handler(Looper.getMainLooper()).postDelayed(this::checkDarkness, 3000);
    }
    private Notification buildNotification(String text) {

        String channelId = "night_safety_channel";

        // Android 8+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Night Safety",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Low visibility safety monitoring");

            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Smart Check-In")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)               // 🔥 foreground service
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private String findTorchCameraId() {
        try {
            CameraManager cm =
                    (CameraManager) getApplicationContext()
                            .getSystemService(Context.CAMERA_SERVICE);

            if (cm == null) return null;

            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc =
                        cm.getCameraCharacteristics(id);

                Boolean hasFlash =
                        cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);

                Integer facing =
                        cc.get(CameraCharacteristics.LENS_FACING);

                if (hasFlash != null && hasFlash
                        && facing != null
                        && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id; // ✅ correct torch camera
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void checkDarkness() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        boolean likelyDark = (hour >= 18 || hour <= 5);

        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, likelyDark);
            }
        } catch (Exception ignored) {}

        // repeat
        new Handler(Looper.getMainLooper()).postDelayed(this::checkDarkness, 3000);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
