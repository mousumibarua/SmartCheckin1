package com.example.smartcheckin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

public class SOSChatDialogFragment extends DialogFragment {

    private static final int SOS_PERMISSION_CODE = 101;
    private static final String EMERGENCY_NUMBER = "+919845115334";

    private EditText edtMessage;
    private LinearLayout chatContainer;
    private ScrollView chatScroll;

    private boolean sosInProgress = false;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View view = inflater.inflate(
                R.layout.dialog_sos_chat,
                container,
                false
        );

        edtMessage = view.findViewById(R.id.edtMessage);
        chatContainer = view.findViewById(R.id.chatContainer);
        chatScroll = view.findViewById(R.id.chatScroll);

        Button btnSend = view.findViewById(R.id.btnSendSOS);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        // Initial menu
        addBotMessage(
                "What is the emergency?\n\n" +
                        "1️⃣ I feel unsafe\n" +
                        "2️⃣ Medical emergency\n" +
                        "3️⃣ Running late / delayed\n" +
                        "4️⃣ Other (type message)"
        );

        btnSend.setOnClickListener(v -> checkPermissions());
        btnCancel.setOnClickListener(v -> dismiss());

        return view;
    }

    /* ---------- POSITION BOTTOM RIGHT ---------- */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.x = 24;
            params.y = 48;
            window.setAttributes(params);
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    /* ---------- CHAT UI ---------- */

    private void addBotMessage(String msg) {
        if (getContext() == null) return;

        TextView tv = new TextView(getContext());
        tv.setText("🤖 " + msg);
        tv.setPadding(20, 12, 20, 12);
        tv.setBackgroundResource(R.drawable.bg_bot);
        chatContainer.addView(tv);
        scroll();
    }

    private void addUserMessage(String msg) {
        if (getContext() == null) return;

        TextView tv = new TextView(getContext());
        tv.setText("👤 " + msg);
        tv.setPadding(20, 12, 20, 12);
        tv.setBackgroundResource(R.drawable.bg_user);
        chatContainer.addView(tv);
        scroll();
    }

    private void scroll() {
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    /* ---------- PERMISSIONS ---------- */

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.SEND_SMS
        ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    SOS_PERMISSION_CODE
            );
        } else {
            processSOS();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SOS_PERMISSION_CODE) {

            boolean smsGranted = false;
            boolean locationGranted = false;

            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.SEND_SMS.equals(permissions[i])) {
                    smsGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])) {
                    locationGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }

            if (smsGranted && locationGranted) {
                processSOS();
            } else {
                Toast.makeText(
                        getContext(),
                        "SMS & Location permission required for SOS",
                        Toast.LENGTH_SHORT
                ).show();
                sosInProgress = false;
            }
        }
    }


    /* ---------- SOS LOGIC ---------- */

    private void processSOS() {
        if (sosInProgress) return;
        sosInProgress = true;

        String input = edtMessage.getText().toString().trim();
        String reason;

        switch (input) {
            case "1":
                reason = "User feels unsafe";
                break;
            case "2":
                reason = "Medical emergency";
                break;
            case "3":
                reason = "User is running late";
                break;
            case "4":
                reason = "Other emergency reported";
                break;
            default:
                reason = input.isEmpty()
                        ? "Emergency assistance needed"
                        : input;
        }

        addUserMessage(input.isEmpty() ? reason : input);
        addBotMessage("Emergency recorded. Contacting help now.");

        sendSOS(reason);
        edtMessage.setText("");
    }

    private void sendSOS(String reason) {

        String location = getOSMLocation();

        String sms =
                "🚨 SOS ALERT\n" +
                        "Reason: " + reason +
                        "\nLocation:\n" + location;

        // 📞 Dial (safe on emulator & phone)
        Intent dial = new Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:" + EMERGENCY_NUMBER)
        );
        if (isAdded()) {
            startActivity(dial);
        }

        try {
            SmsManager.getDefault().sendTextMessage(
                    EMERGENCY_NUMBER,
                    null,
                    sms,
                    null,
                    null
            );
        } catch (Exception ignored) {}

        Toast.makeText(
                getContext(),
                "SOS sent",
                Toast.LENGTH_SHORT
        ).show();

        // ✅ reset so user can send again if needed
        sosInProgress = false;
    }

    /* ---------- LOCATION ---------- */

    private String getOSMLocation() {
        LocationManager lm =
                (LocationManager) requireContext()
                        .getSystemService(android.content.Context.LOCATION_SERVICE);

        if (lm == null) return "Location unavailable";

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {
            return "Location permission denied";
        }

        Location loc = lm.getLastKnownLocation(
                LocationManager.NETWORK_PROVIDER
        );

        if (loc != null) {
            return "https://www.openstreetmap.org/?mlat="
                    + loc.getLatitude() +
                    "&mlon=" + loc.getLongitude();
        }

        return "Location unavailable";
    }
}
