package com.example.smartcheckin;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class VoiceEngine {

    private static TextToSpeech tts;

    public static void speak(Context context, String message) {

        if (tts == null) {
            tts = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US);
                    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            });
        } else {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
}
