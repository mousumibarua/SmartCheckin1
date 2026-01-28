package com.example.smartcheckin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SOSActivity extends AppCompatActivity {

    private static final int SOS_PERMISSION_CODE = 101;
    private static final String EMERGENCY_NUMBER = "+919845115334";

    private EditText edtMessage;
    private ScrollView chatScroll;
    private LinearLayout chatContainer;

    private boolean sosInProgress = false;
    private boolean sosTriggered = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_sos);

        edtMessage = findViewById(R.id.edtMessage);
        chatScroll = findViewById(R.id.chatScroll);
        chatContainer = findViewById(R.id.chatContainer);

        Button btnSendSOS = findViewById(R.id.btnSendSOS);
        Button btnExit = findViewById(R.id.btnExit);

        // ✅ Initial bot message (ONLY ONCE)
        addBotMessage("What is the emergency?");

        // ✅ Single click listener
        btnSendSOS.setOnClickListener(v -> checkPermissions());

        btnExit.setOnClickListener(v -> {
            Toast.makeText(this, "SOS cancelled", Toast.LENGTH_SHORT).show();
            finish();
        });

        // ✅ Modern back handling
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        new AlertDialog.Builder(SOSActivity.this)
                                .setTitle("Exit SOS?")
                                .setMessage("Are you sure you want to cancel the SOS?")
                                .setPositiveButton("Yes", (d, w) -> finish())
                                .setNegativeButton("No", null)
                                .show();
                    }
                });
    }

    /* ================= CHAT UI ================= */

    private void addBotMessage(String msg) {
        View view = getLayoutInflater()
                .inflate(R.layout.item_bot_message, chatContainer, false);

        TextView msgView = view.findViewById(R.id.txtBotMsg);
        TextView timeView = view.findViewById(R.id.txtTime);

        msgView.setText(msg);
        timeView.setText(getTime());

        chatContainer.addView(view);
        scrollChat();
    }

    private void addUserMessage(String msg) {
        View view = getLayoutInflater()
                .inflate(R.layout.item_user_message, chatContainer, false);

        TextView msgView = view.findViewById(R.id.txtUserMsg);
        TextView timeView = view.findViewById(R.id.txtTime);

        msgView.setText(msg);
        timeView.setText(getTime());

        chatContainer.addView(view);
        scrollChat();
    }

    /* ================= PERMISSIONS ================= */

    private void checkPermissions() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    SOS_PERMISSION_CODE
            );
        } else {
            processChatAndSendSOS();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SOS_PERMISSION_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            processChatAndSendSOS();
        } else {
            Toast.makeText(this,
                    "Required permissions denied",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /* ================= CHAT + SOS LOGIC ================= */

    private void processChatAndSendSOS() {
        if (sosInProgress) return;
        sosInProgress = true;

        String userMsg = edtMessage.getText().toString().trim();
        if (userMsg.isEmpty()) {
            userMsg = "Emergency assistance needed";
        }

        addUserMessage(userMsg);

        String lower = userMsg.toLowerCase();
        String botReply;

        if (lower.contains("unsafe") || lower.contains("medical")) {
            botReply = "Emergency detected. Contacting help now.";
        } else if (lower.contains("late")) {
            botReply = "Delay reported. Informing security.";
        } else {
            botReply = "Help is being contacted. Stay calm.";
        }

        addBotMessage(botReply);

        sendSOS(userMsg);
    }

    /* ================= SOS ACTION ================= */

    private void sendSOS(String reason) {
        sosTriggered = true;

        String locationLink = getOSMLocationLink();

        String smsMessage =
                "🚨 SOS ALERT\n" +
                        "Reason: " + reason + "\n\n" +
                        "Location:\n" + locationLink;

        // 📞 Dial (works on emulator + phone)
        Intent dial = new Intent(Intent.ACTION_DIAL);
        dial.setData(Uri.parse("tel:" + EMERGENCY_NUMBER));
        startActivity(dial);

        // 📩 SMS
        try {
            SmsManager.getDefault().sendTextMessage(
                    EMERGENCY_NUMBER,
                    null,
                    smsMessage,
                    null,
                    null
            );
            Log.d("SOS_SMS", "SMS sent");
        } catch (Exception e) {
            Toast.makeText(this, "SMS failed", Toast.LENGTH_SHORT).show();
        }

        Toast.makeText(this, "SOS sent", Toast.LENGTH_LONG).show();
    }

    /* ================= LOCATION ================= */

    private String getOSMLocationLink() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return "Location unavailable";

            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return "Location permission denied";
            }

            Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (loc != null) {
                return "https://www.openstreetmap.org/?mlat="
                        + loc.getLatitude() + "&mlon=" + loc.getLongitude()
                        + "#map=18/" + loc.getLatitude() + "/" + loc.getLongitude();
            }
        } catch (Exception ignored) {}

        return "Location unavailable";
    }

    /* ================= UTIL ================= */

    private void scrollChat() {
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private String getTime() {
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("hh:mm a",
                        java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sosTriggered) {
            addBotMessage("Emergency call initiated. Help is on the way.");
            sosTriggered = false;
            sosInProgress = false;
        }
    }
}
