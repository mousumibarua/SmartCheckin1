package com.example.smartcheckin;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class HapticEngine {

    public static void shortPulse(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    public static void turnVibration(Context context) {
        long[] pattern = {0, 200, 100, 200};
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }

    public static void dangerPulse(Context context) {
        long[] pattern = {0, 300, 100, 300, 100, 300};
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }

    public static void morseDirection(Context context, String direction) {
        long[] pattern = direction.contains("left")
                ? new long[]{0,100,100,300}
                : new long[]{0,300,100,100};
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }
}
