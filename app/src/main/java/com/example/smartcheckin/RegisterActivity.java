package com.example.smartcheckin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtPhone, edtRegId;
    private Button btnSendOtp;
    private ProgressBar progressOtp;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // 🔗 Bind views
        edtPhone = findViewById(R.id.edtPhone);
        edtRegId = findViewById(R.id.edtRegId);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        progressOtp = findViewById(R.id.progressOtp);

        auth = FirebaseAuth.getInstance();

        // 🔐 Skip if already verified
        SharedPreferences prefs =
                getSharedPreferences("SmartCheckinPrefs", MODE_PRIVATE);

        if (prefs.getBoolean("isVerified", false)) {
            startActivity(new Intent(this, LockActivity.class));
            finish();
            return;
        }

        btnSendOtp.setOnClickListener(v -> sendOtp());
    }

    private void sendOtp() {

        String phone = edtPhone.getText().toString().trim();
        String regId = edtRegId.getText().toString().trim();

        // ✅ Validation
        if (phone.length() != 10) {
            edtPhone.setError("Enter valid 10-digit number");
            return;
        }

        if (regId.isEmpty()) {
            edtRegId.setError("Enter registration ID");
            return;
        }

        // 🔄 SHOW SPINNER
        progressOtp.setVisibility(View.VISIBLE);
        btnSendOtp.setEnabled(false);

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber("+91" + phone)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(callbacks)
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    // 📲 Firebase Callbacks
    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                @Override
                public void onVerificationCompleted(
                        @NonNull PhoneAuthCredential credential) {
                    // Auto-verification (rare)
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {

                    // ❌ HIDE SPINNER ON FAILURE
                    progressOtp.setVisibility(View.GONE);
                    btnSendOtp.setEnabled(true);

                    Toast.makeText(RegisterActivity.this,
                            "OTP failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCodeSent(@NonNull String verificationId,
                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {

                    // ✅ HIDE SPINNER WHEN OTP ARRIVES
                    progressOtp.setVisibility(View.GONE);
                    btnSendOtp.setEnabled(true);

                    // 🔔 SIMULATED OTP POPUP (DEMO)
                    new AlertDialog.Builder(RegisterActivity.this)
                            .setTitle("📩 New SMS")
                            .setMessage("Your SmartCheckin OTP is 123456")
                            .setCancelable(false)
                            .setPositiveButton("OK", (dialog, which) -> {

                                Intent intent =
                                        new Intent(RegisterActivity.this, OtpActivity.class);
                                intent.putExtra("verificationId", verificationId);
                                intent.putExtra("phone",
                                        edtPhone.getText().toString().trim());
                                intent.putExtra("regId",
                                        edtRegId.getText().toString().trim());
                                startActivity(intent);
                            })
                            .show();
                }
            };
}
