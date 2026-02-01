package com.example.smartcheckin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class OtpActivity extends AppCompatActivity {

    private EditText edtOtp;
    private Button btnVerify;
    private ProgressBar progressVerify;

    private String phone, regId;

    // 🔐 DEMO OTP
    private static final String DEMO_OTP = "123456";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        edtOtp = findViewById(R.id.edtOtp);
        btnVerify = findViewById(R.id.btnVerifyOtp);
        progressVerify = findViewById(R.id.progressVerify);

        phone = getIntent().getStringExtra("phone");
        regId = getIntent().getStringExtra("regId");

        btnVerify.setOnClickListener(v -> {

            String otp = edtOtp.getText().toString().trim();

            if (otp.length() != 6) {
                edtOtp.setError("Enter 6-digit OTP");
                return;
            }

            // 🔄 SHOW SPINNER
            progressVerify.setVisibility(View.VISIBLE);
            btnVerify.setEnabled(false);

            verifyOtp(otp);
        });
    }

    private void verifyOtp(String enteredOtp) {

        // ❌ INVALID OTP
        if (!DEMO_OTP.equals(enteredOtp)) {
            progressVerify.setVisibility(View.GONE);
            btnVerify.setEnabled(true);

            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        onOtpVerified();
        // ✅ OTP VERIFIED → SAVE USER
        saveUserToFirestore();
    }
    private void onOtpVerified() {

        // ⏹ STOP SPINNER
        progressVerify.setVisibility(View.GONE);
        btnVerify.setEnabled(true);

        // ✅ SAVE VERIFIED STATE LOCALLY
        SharedPreferences prefs =
                getSharedPreferences("SmartCheckinPrefs", MODE_PRIVATE);

        prefs.edit()
                .putBoolean("isVerified", true)
                .putString("phone", phone)
                .apply();

        // ✅ SHOW SUCCESS MESSAGE
        Toast.makeText(
                OtpActivity.this,
                "OTP verified successfully",
                Toast.LENGTH_SHORT
        ).show();

        // ⏳ SHORT DELAY → SET PIN SCREEN
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {

                    Intent intent =
                            new Intent(OtpActivity.this, LockActivity.class);
                    intent.setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                    );
                    startActivity(intent);
                    finish();

                }, 800);
    }

    private void saveUserToFirestore() {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> user = new HashMap<>();
        user.put("phone", phone);
        user.put("registrationId", regId);
        user.put("verified", true);
        user.put("timestamp", System.currentTimeMillis());

        // ☁️ Firestore save should NOT block OTP success flow
        db.collection("users")
                .document(phone)
                .set(user)
                .addOnSuccessListener(unused -> {
                    // Optional: success log only (no UI work here)
                })
                .addOnFailureListener(e -> {
                    // Optional: failure log only (no UI work here)
                });
    }

}
