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

    GeoPoint campus = new GeoPoint(12.9716, 77.5946);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

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
            Toast.makeText(this, "Checked in successfully!", Toast.LENGTH_SHORT).show();
        });

        btnRoute.setOnClickListener(v ->
                startActivity(new Intent(this, RouteActivity.class)));

        btnSOS.setOnClickListener(v ->
                new SOSChatDialogFragment()
                        .show(getSupportFragmentManager(), "SOS_CHAT"));

        setupMap();
        updateUI();
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
}
