package com.example.smartcheckin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegNumberActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reg_number);

        EditText edtReg = findViewById(R.id.edtRegNo);
        Button btnSubmit = findViewById(R.id.btnSubmit);

        btnSubmit.setOnClickListener(v -> {
            String regNo = edtReg.getText().toString().trim();

            if (regNo.isEmpty()) {
                Toast.makeText(this,
                        "Enter registration number",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            saveToFirestore(regNo);
        });
    }

    /* ================= SAVE USER ================= */

    private void saveToFirestore(String regNo) {

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> user = new HashMap<>();
        user.put("phone", auth.getCurrentUser().getPhoneNumber());
        user.put("regNo", regNo);
        user.put("createdAt", System.currentTimeMillis());

        db.collection("users")
                .document(auth.getUid())
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    cacheLocally();
                    startActivity(
                            new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to save user",
                                Toast.LENGTH_SHORT).show());
    }

    /* ================= LOCAL CACHE ================= */

    private void cacheLocally() {
        getSharedPreferences("user", MODE_PRIVATE)
                .edit()
                .putBoolean("loggedIn", true)
                .apply();
    }
}
