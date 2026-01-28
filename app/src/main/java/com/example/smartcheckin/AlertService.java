package com.example.smartcheckin;

import android.app.*;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.*;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;

public class AlertService extends Service {

    private Handler handler;
    private MediaPlayer mp;

    int[] intervals = {5, 10, 15};

    Runnable alertRunnable = new Runnable() {
        int index = 0;

        @Override
        public void run() {
            if (index < intervals.length) {
                triggerAlert();
                handler.postDelayed(this, intervals[index++] * 60 * 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        startForegroundNotification();

        if (!Utils.isCheckedOut(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        scheduleDeadlineAlerts();
        return START_STICKY;
    }

    private void triggerAlert() {

        if (!Utils.isCheckedOut(this)) {
            stopSelf();
            return;
        }

        if (isBeforeDeadline()) {
            playUrgentAlert();
        }
    }

    private boolean isBeforeDeadline() {

        Calendar now = Calendar.getInstance();
        Calendar deadline = Calendar.getInstance();

       // deadline.set(Calendar.HOUR_OF_DAY, 20); // 7 PM ✅
        deadline.set(Calendar.MINUTE, 5);
      //  deadline.set(Calendar.SECOND, 0);

        return now.before(deadline);
    }

    private void playUrgentAlert() {

        if (mp != null) return; // already playing

        mp = MediaPlayer.create(this, R.raw.alert_sound);

        if (mp == null) {
            // Audio failed — DO NOT CRASH
            return;
        }

        mp.setLooping(true);
        mp.start();

        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(
                    VibrationEffect.createWaveform(
                            new long[]{0, 1000, 500},
                            0
                    )
            );
        }
    }

    private void startForegroundNotification() {

        String channelId = "alert_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Check-out Alerts",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        Notification notification =
                new NotificationCompat.Builder(this, channelId)
                        .setContentTitle("Check-out active")
                        .setContentText("Alerts are running")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setOngoing(true)
                        .build();

        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        if (mp != null) {
            mp.stop();
            mp.release();
            mp = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    private void scheduleDeadlineAlerts() {

        handler.removeCallbacksAndMessages(null);

        // Deadline = 7:00 PM today
        Calendar deadline = Calendar.getInstance();
        deadline.set(Calendar.HOUR_OF_DAY, 19);
        deadline.set(Calendar.MINUTE, 0);
        deadline.set(Calendar.SECOND, 0);
        deadline.set(Calendar.MILLISECOND, 0);

        // Alerts at −15, −10, −5, and 0 minutes
        int[] beforeMinutes = {15, 10, 5, 0};

        for (int mins : beforeMinutes) {

            Calendar alertTime = (Calendar) deadline.clone();
            alertTime.add(Calendar.MINUTE, -mins);

            long delayMillis =
                    alertTime.getTimeInMillis() - System.currentTimeMillis();

            // Schedule only if still in the future
            if (delayMillis > 0) {

                handler.postDelayed(() -> {

                    if (Utils.isCheckedOut(this)) {

                        playUrgentAlert();

                        if (mins == 0) {
                            Log.e("ALERT", "🚨 FINAL ALERT at 7:00 PM");
                        } else {
                            Log.e("ALERT",
                                    "⚠ Alert fired " + mins + " min before deadline");
                        }
                    }

                }, delayMillis);
            }
        }
    }
}
