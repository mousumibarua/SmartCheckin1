package com.example.smartcheckin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LockActivity extends AppCompatActivity {

    private static final String PREFS = "SmartCheckinPrefs";
    private static final String KEY_PIN = "PIN";

    private TextView txtTitle, txtPinDots;
    private Button btnSOS;
    private final StringBuilder enteredPin = new StringBuilder();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_lock);

        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        boolean isVerified = prefs.getBoolean("isVerified", false);

        // 🔐 If user never registered → go to Register
        if (!isVerified) {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
            return;
        }

        txtTitle = findViewById(R.id.txtTitle);
        txtPinDots = findViewById(R.id.txtPinDots);
        btnSOS = findViewById(R.id.btnSOS);

        boolean isPinSet = prefs.contains(KEY_PIN);

        txtTitle.setText(isPinSet ? "Enter PIN" : "Set PIN");
        updateDots(0);

        // 🔢 Bubble keypad
        GridLayout keypad = findViewById(R.id.pinGrid);

        for (int i = 0; i < keypad.getChildCount(); i++) {
            View v = keypad.getChildAt(i);
            if (!(v instanceof Button)) continue;

            Button btn = (Button) v;
            String value = btn.getText().toString();

            btn.setOnClickListener(view ->
                    handleKeyPress(value, prefs));
        }

        // 🚨 SOS BYPASS
        btnSOS.setOnClickListener(v ->
                new SOSChatDialogFragment()
                        .show(getSupportFragmentManager(), "SOS_CHAT")
        );
    }

    /* ================= KEYPAD LOGIC ================= */

    private void handleKeyPress(String value,
                                SharedPreferences prefs) {

        if ("⌫".equals(value)) {
            if (enteredPin.length() > 0) {
                enteredPin.deleteCharAt(enteredPin.length() - 1);
            }
        } else if (enteredPin.length() < 4) {
            enteredPin.append(value);
        }

        updateDots(enteredPin.length());

        if (enteredPin.length() == 4) {

            String pin = enteredPin.toString();
            boolean isPinSet = prefs.contains(KEY_PIN);

            if (!isPinSet) {
                // 🔐 FIRST TIME → SAVE PIN
                prefs.edit()
                        .putString(KEY_PIN, pin)
                        .apply();

                Toast.makeText(this,
                        "PIN set successfully",
                        Toast.LENGTH_SHORT).show();

                enteredPin.setLength(0);
                openMain();

            } else {
                // 🔓 VALIDATE PIN
                String savedPin = prefs.getString(KEY_PIN, "");

                if (pin.equals(savedPin)) {
                    enteredPin.setLength(0);
                    openMain();
                } else {
                    Toast.makeText(this,
                            "Incorrect PIN",
                            Toast.LENGTH_SHORT).show();

                    enteredPin.setLength(0);
                    updateDots(0);
                }
            }
        }
    }

    /* ================= UI HELPERS ================= */

    private void updateDots(int count) {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            dots.append(i < count ? "● " : "○ ");
        }
        txtPinDots.setText(dots.toString().trim());
    }

    /* ================= NAVIGATION ================= */

    private void openMain() {
        Intent intent =
                new Intent(LockActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
