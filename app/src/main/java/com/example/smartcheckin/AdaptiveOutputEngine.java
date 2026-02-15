package com.example.smartcheckin;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AdaptiveOutputEngine {

    private Context context;

    public AdaptiveOutputEngine(Context context) {
        this.context = context;
    }

    private void vibrate(long durationMs) {

        Vibrator vibrator =
                (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            vibrator.vibrate(
                    VibrationEffect.createOneShot(
                            durationMs,
                            VibrationEffect.DEFAULT_AMPLITUDE
                    )
            );

        } else {
            vibrator.vibrate(durationMs); // API 24–25 safe
        }
    }
    private AccessibilityProfile currentProfile = AccessibilityProfile.STANDARD;

    public void setProfile(AccessibilityProfile profile) {
        this.currentProfile = profile;
    }

    public void notifyTurn(String message, String riskLevel) {

        // Speak message
        VoiceEngine.speak(context, message);

        // Optional vibration based on risk
        if ("RED".equals(riskLevel)) {
            vibrate( 800);
        } else if ("YELLOW".equals(riskLevel)) {
            vibrate( 400);
        } else {
            vibrate(150);
        }
    }
    public void notifyHighRisk() {

        // Speak warning
        VoiceEngine.speak(context,
                "Caution. You are on a higher risk route.");

        // Strong vibration pattern
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            android.os.Vibrator vibrator =
                    (android.os.Vibrator) context.getSystemService(
                            android.content.Context.VIBRATOR_SERVICE);

            if (vibrator != null) {
                vibrator.vibrate(
                        android.os.VibrationEffect.createOneShot(
                                1200,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE
                        )
                );
            }

        } else {

            android.os.Vibrator vibrator =
                    (android.os.Vibrator) context.getSystemService(
                            android.content.Context.VIBRATOR_SERVICE);

            if (vibrator != null) {
                vibrator.vibrate(1200);
            }
        }
    }


}
