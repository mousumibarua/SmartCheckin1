package com.example.smartcheckin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import android.graphics.drawable.GradientDrawable;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import com.google.firebase.analytics.FirebaseAnalytics;
import android.view.Menu;
import android.view.MenuItem;
import android.app.AlertDialog;
import android.net.Uri;
import android.provider.MediaStore;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "checkin_alerts";

    MapView map;
    MyLocationNewOverlay locationOverlay;

    Button btnCheckout, btnCheckin, btnSOS, btnRoute;
    TextView txtStatus, txtCheckoutTime, txtCountdown;
    LinearLayout layoutCountdown;

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private Animation pulseAnim;

    private boolean alert15Shown = false;
    private boolean alert10Shown = false;
    private boolean alert5Shown  = false;
    private boolean violationShown = false;
    private FirebaseAnalytics firebaseAnalytics;
    private static final int REQ_PHOTO = 501;
    private static final int REQ_VIDEO = 502;
    private static final int REQ_CAMERA = 200;

    GeoPoint campus = new GeoPoint(12.9716, 77.5946);
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.menu_logout) {
            logoutUser();
            return true;
        }

        if (id == R.id.menu_deregister) {
            deregisterUser();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        Bundle bundle = new Bundle();
        bundle.putString("screen", "MainActivity");
        firebaseAnalytics.logEvent("app_opened", bundle);


        txtStatus = findViewById(R.id.txtStatus);
        txtCheckoutTime = findViewById(R.id.txtCheckoutTime);
        txtCountdown = findViewById(R.id.txtCountdown);
        layoutCountdown = findViewById(R.id.layoutCountdown);

        btnCheckout = findViewById(R.id.btnCheckout);
        btnCheckin = findViewById(R.id.btnCheckin);
        btnSOS = findViewById(R.id.btnSOS);
        btnRoute = findViewById(R.id.btnRoute);

        pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse);

        btnCheckout.setOnClickListener(v -> startCheckoutFlow());

        btnCheckin.setOnClickListener(v -> {
            Utils.setCheckedOut(this, false);
            resetAlerts();
            updateUI();
           // Bundle bundle = new Bundle();
            bundle.putString("action", "checkin");
            firebaseAnalytics.logEvent("user_checkin", bundle);

            Toast.makeText(this, "Checked in successfully!", Toast.LENGTH_SHORT).show();
        });

        btnRoute.setOnClickListener(v ->
                startActivity(new Intent(this, RouteActivity.class)));

     /*   btnSOS.setOnClickListener(v ->
                new SOSChatDialogFragment()
                        .show(getSupportFragmentManager(), "SOS_CHAT"));*/
        btnSOS.setOnClickListener(v -> showSosMediaChoice());

        setupMap();
        updateUI();
    }
    private void showSosMediaChoice() {

        String[] options = {"📸 Take Photo", "🎥 Record Video", "Skip"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Add media to SOS?")
                .setItems(options, (dialog, which) -> {

                    if (which == 0) {
                        if (ensureCameraPermission()) {
                            openCameraPhoto();
                        }
                    } else if (which == 1) {
                        if (ensureCameraPermission()) {
                            openCameraVideo();
                        }
                    } else {
                        openSosChat();
                    }
                })
                .setCancelable(true)
                .show();
    }
    private void openCameraPhoto() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, REQ_PHOTO);
    }
    private void openCameraVideo() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(android.provider.MediaStore.EXTRA_DURATION_LIMIT, 15);
        startActivityForResult(intent, REQ_VIDEO);
    }
    private void openSosChat() {
        new SOSChatDialogFragment()
                .show(getSupportFragmentManager(), "SOS_CHAT");
    }

    private boolean ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.CAMERA},
                    REQ_CAMERA
            );
            return false;
        }
        return true;
    }
    private void showSosMediaDialog() {

        new AlertDialog.Builder(this)
                .setTitle("🚨 SOS Alert")
                .setMessage("Do you want to add a photo or video?")
                .setPositiveButton("Yes", (d, w) -> showMediaOptions())
                .setNegativeButton("No", (d, w) -> sendSosWithoutMedia())
                .show();
    }
    private void showMediaOptions() {

        String[] options = {"Take Photo", "Record Video"};

        new AlertDialog.Builder(this)
                .setTitle("Add Media")
                .setItems(options, (dialog, which) -> {

                    if (which == 0) {
                        Intent photoIntent =
                                new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        startActivityForResult(photoIntent, REQ_PHOTO);
                    } else {
                        Intent videoIntent =
                                new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                        startActivityForResult(videoIntent, REQ_VIDEO);
                    }
                })
                .show();
    }

    /* ================= MAP ================= */

    private void setupMap() {
        Configuration.getInstance().setUserAgentValue(getPackageName());
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        locationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), map);
        locationOverlay.enableMyLocation();
        map.getOverlays().add(locationOverlay);

        map.getController().setZoom(5.0);
        map.getController().setCenter(campus);

        Marker marker = new Marker(map);
        marker.setPosition(campus);
        marker.setTitle("Campus");
        map.getOverlays().add(marker);

        drawGeofence(campus, 300);
    }

    /* ================= CHECKOUT ================= */

    private void startCheckoutFlow() {
        Utils.setCheckedOut(this, true);
        resetAlerts();
        updateUI();
        Toast.makeText(this, "Checked out! Monitoring started.", Toast.LENGTH_SHORT).show();
        Bundle bundle = new Bundle();
        bundle.putString("action", "checkout");
        firebaseAnalytics.logEvent("user_checkout", bundle);

    }

    private void resetAlerts() {
        alert15Shown = alert10Shown = alert5Shown = violationShown = false;
    }

    /* ================= UI ================= */

    private void updateUI() {

        long checkoutTime = Utils.getCheckoutTime(this);

        if (Utils.isCheckedOut(this)) {

            long deadlineMs = checkoutTime + (15 * 60 * 1000);

            if (System.currentTimeMillis() > deadlineMs) {
                txtStatus.setText("You are checked out, deadline lapsed!!");
            } else {
                txtStatus.setText("You are checked out");
            }

            txtStatus.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_red_dark)
            );

            txtCheckoutTime.setText(
                    "Checked out at " +
                            DateFormat.format("hh:mm a", checkoutTime));
            txtCheckoutTime.setVisibility(View.VISIBLE);

            btnCheckout.setEnabled(false);
            btnCheckin.setEnabled(true);
            btnRoute.setEnabled(true);

            startCountdown();

        } else {

            txtStatus.setText("You are checked in");
            txtStatus.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_green_dark));

            txtCheckoutTime.setVisibility(View.GONE);
            layoutCountdown.setVisibility(View.GONE);

            btnCheckout.setEnabled(true);
            btnCheckin.setEnabled(false);
            btnRoute.setEnabled(false);

            countdownHandler.removeCallbacksAndMessages(null);
        }
    }

    /* ================= COUNTDOWN ================= */

    private void startCountdown() {

        countdownHandler.removeCallbacksAndMessages(null);

        long checkoutTime = Utils.getCheckoutTime(this);
        long deadline = checkoutTime + (15 * 60 * 1000);

        countdownRunnable = () -> {

            long remaining = deadline - System.currentTimeMillis();

            if (remaining <= 0) {
                if (!violationShown) {
                    violationShown = true;
                    Bundle bundle = new Bundle();
                    bundle.putString("violation", "checkout_deadline");
                    firebaseAnalytics.logEvent("deadline_violated", bundle);
                    showNotification(
                            104,
                            "Check-in Deadline Violated",
                            "❌ Deadline violated on " +
                                    DateFormat.format("dd MMM yyyy, hh:mm a",
                                            new java.util.Date())
                    );
                }
                layoutCountdown.setVisibility(View.GONE);
                return;
            }

            long min = remaining / 60000;
            long sec = (remaining / 1000) % 60;

            layoutCountdown.setVisibility(View.VISIBLE);
            txtCountdown.setText(String.format("%02d:%02d", min, sec));

            GradientDrawable bg =
                    (GradientDrawable) layoutCountdown.getBackground();

            if (remaining > 10 * 60 * 1000) {
                bg.setColor(0xFF2E7D32);
                if (!alert15Shown) {
                    alert15Shown = true;
                    showNotification(101,
                            "Check-in Reminder",
                            "🟢 15 minutes left to check in");
                }
            } else if (remaining > 5 * 60 * 1000) {
                bg.setColor(0xFFF9A825);
                if (!alert10Shown) {
                    alert10Shown = true;
                    showNotification(102,
                            "Check-in Warning",
                            "🟡 10 minutes left to check in");
                }
            } else {
                bg.setColor(0xFFC62828);
                if (!alert5Shown) {
                    alert5Shown = true;
                    showNotification(103,
                            "Urgent Check-in",
                            "🔴 Only 5 minutes left!");
                }
                if (txtCountdown.getAnimation() == null) {
                    txtCountdown.startAnimation(pulseAnim);
                }
            }

            countdownHandler.postDelayed(countdownRunnable, 1000);
        };

        countdownHandler.post(countdownRunnable);
    }

    /* ================= NOTIFICATIONS ================= */

    private void showNotification(int id, String title, String msg) {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(title)
                        .setContentText(msg)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        NotificationManagerCompat nm = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED) {

            nm.notify(id, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Check-in Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    /* ================= GEOFENCE ================= */

    void drawGeofence(GeoPoint center, int radiusMeters) {
        Polygon circle = new Polygon();
        circle.setPoints(Polygon.pointsAsCircle(center, radiusMeters));
        circle.setFillColor(0x121111FF);
        circle.setStrokeColor(0xFF0000FF);
        circle.setStrokeWidth(3f);
        map.getOverlays().add(circle);
    }
    private void deregisterUser() {

        // 1️⃣ Clear SharedPreferences
        getSharedPreferences("SmartCheckinPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        // 2️⃣ Optional: Firebase sign out (safe even if not used)
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {}

        // 3️⃣ Go back to Register screen
        Intent intent = new Intent(this, RegisterActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        finish();
    }
    private void logoutUser() {

        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LockActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void uploadMediaAndSendSos(Uri uri) {

        Toast.makeText(this, "Uploading media...", Toast.LENGTH_SHORT).show();

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference ref =
                storage.getReference("sos_media/" + System.currentTimeMillis());

        ref.putFile(uri)
                .continueWithTask(task -> ref.getDownloadUrl())
                .addOnSuccessListener(downloadUri ->
                        sendSosWithMedia(downloadUri.toString()))
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Upload failed",
                                Toast.LENGTH_SHORT).show());
    }
    private void sendSosWithoutMedia() {

        Toast.makeText(this,
                "SOS sent without media",
                Toast.LENGTH_SHORT).show();

        // Optional: keep your SOS chat fragment
        new SOSChatDialogFragment()
                .show(getSupportFragmentManager(), "SOS_CHAT");
    }
    private void sendSosWithMedia(String mediaUrl) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> sos = new HashMap<>();
        sos.put("mediaUrl", mediaUrl);
        sos.put("time", System.currentTimeMillis());
        sos.put("type", "media_sos");

        db.collection("sos_alerts")
                .add(sos)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this,
                                "SOS sent with media",
                                Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to send SOS",
                                Toast.LENGTH_SHORT).show());
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // If user cancelled camera → still open SOS chat
        if (resultCode != RESULT_OK) {
            openSosChat();
            return;
        }

        // Photo or video captured
        if (requestCode == REQ_PHOTO || requestCode == REQ_VIDEO) {

            // Optional: you can process / upload media here later

            // ✅ ALWAYS open SOS chat after returning
            openSosChat();
        }
    }


}
