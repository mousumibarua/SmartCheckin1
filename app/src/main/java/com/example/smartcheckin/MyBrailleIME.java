package com.example.smartcheckin;

import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.ImageView;
import java.util.HashMap;
import java.util.Map;

public class MyBrailleIME extends InputMethodService implements View.OnTouchListener {

    private View keyboardView;
    private int pressedDots = 0; // bitmask: dot1=1, dot2=2, dot3=4, dot4=8, dot5=16, dot6=32

    // Very basic English Grade 1 mapping (expand this!)
    private static final Map<Integer, String> brailleToChar = new HashMap<>();
    static {
        brailleToChar.put(1, "a");      // ⠁
        brailleToChar.put(3, "b");      // ⠃
        brailleToChar.put(9, "c");      // ⠉
        brailleToChar.put(25, "d");     // ⠙
        brailleToChar.put(17, "e");     // ⠑
        brailleToChar.put(11, "f");     // ⠋
        // Add full table: https://en.wikipedia.org/wiki/Braille_ASCII
        brailleToChar.put(63, " ");     // space example
        // ...
    }

    @Override
    public View onCreateInputView() {
        keyboardView = getLayoutInflater().inflate(R.layout.braille_keyboard, null);

        int[] dotIds = {R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4, R.id.dot5, R.id.dot6};
        for (int id : dotIds) {
            ImageView dot = keyboardView.findViewById(id);
            dot.setOnTouchListener(this);
        }

        Button btnSpace = keyboardView.findViewById(R.id.btn_space);
        btnSpace.setOnClickListener(v -> commitText(" "));

        Button btnClear = keyboardView.findViewById(R.id.btn_clear);
        btnClear.setOnClickListener(v -> pressedDots = 0);

        Button btnDelete = keyboardView.findViewById(R.id.btn_delete);
        btnDelete.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.deleteSurroundingText(1, 0);
        });

        return keyboardView;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Integer bit = (Integer) v.getTag();
        if (bit == null) return false;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressedDots |= bit;
                vibrate(30);
                // Optional: highlight dot
                v.setBackgroundResource(R.drawable.circle_pressed);
                break;

            case MotionEvent.ACTION_UP:
                // Commit on release (you can also use 400ms timeout)
                String ch = brailleToChar.getOrDefault(pressedDots, "?");
                if (!ch.equals("?")) {
                    commitText(ch);
                }
                pressedDots = 0;
                // Reset all dots visually
                resetDotColors();
                break;
        }
        return true;
    }

    private void vibrate(long ms) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }

    private void commitText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    private void resetDotColors() {
        int[] dotIds = {R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4, R.id.dot5, R.id.dot6};
        for (int id : dotIds) {
            ImageView dot = keyboardView.findViewById(id);
            dot.setBackgroundResource(R.drawable.circle_gray);
        }
    }
}