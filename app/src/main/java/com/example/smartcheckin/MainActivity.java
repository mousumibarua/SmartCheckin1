package com.example.smartcheckin;

import android.content.Intent;
import android.os.*;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.Calendar;

import android.graphics.drawable.GradientDrawable;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

public class MainActivity extends AppCompatActivity {

    MapView map;
    MyLocationNewOverlay locationOverlay;
    Handler handler = new Handler();

    GeoPoint campus = new GeoPoint(12.9716, 77.5946);

    Polyline routeLine;
    Button btnCheckout, btnCheckin, btnSOS, btnRoute;
    TextView txtStatus, txtCheckoutTime, txtCountdown;
    LinearLayout layoutCountdown;

    private Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private Animation pulseAnim;

    // 🔴 SOS escalation flags
    private boolean warned10 = false;
    private boolean warned5 = false;
    private boolean timeOverHandled = false;

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        countdownHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        txtCheckoutTime = findViewById(R.id.txtCheckoutTime);
        txtCountdown = findViewById(R.id.txtCountdown);
        layoutCountdown = findViewById(R.id.layoutCountdown);

        btnCheckout = findViewById(R.id.btnCheckout);
        btnCheckin = findViewById(R.id.btnCheckin);
        btnSOS = findViewById(R.id.btnSOS);
        btnRoute = findViewById(R.id.btnRoute);

        pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse);

        updateUI();

        btnCheckout.setOnClickListener(v -> startCheckoutFlow());

        btnCheckin.setOnClickListener(v -> {
            Utils.setCheckedOut(this, false);
            stopService(new Intent(this, AlertService.class));

            // reset flags
            warned10 = false;
            warned5 = false;
            timeOverHandled = false;

            Toast.makeText(this, "Checked in successfully!", Toast.LENGTH_SHORT).show();
            updateUI();
        });

        btnRoute.setOnClickListener(v ->
                startActivity(new Intent(this, RouteActivity.class)));

        // ✅ SOS opens ONLY on button click
        btnSOS.setOnClickListener(v ->
                startActivity(new Intent(this, SOSActivity.class)));

        // MAP SETUP
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

    private void startCheckoutFlow() {

        Utils.setCheckedOut(this, true);

        warned10 = false;
        warned5 = false;
        timeOverHandled = false;

        updateUI();

        startAlertService();

        Toast.makeText(this, "Checked out! Monitoring started.", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {

        long checkoutTime = Utils.getCheckoutTime(this);

        if (Utils.isCheckedOut(this)) {

            txtStatus.setText("You are checked out");
            txtStatus.setTextColor(ContextCompat.getColor(this,
                    android.R.color.holo_red_dark));

            if (checkoutTime > 0) {
                txtCheckoutTime.setText(
                        "Checked out at " +
                                DateFormat.format("hh:mm a", checkoutTime));
                txtCheckoutTime.setVisibility(View.VISIBLE);
            }

            btnCheckout.setEnabled(false);
            btnCheckin.setEnabled(true);
            btnRoute.setEnabled(true);

            startCountdownIfNeeded();

        } else {

            txtStatus.setText("You are checked in");
            txtStatus.setTextColor(ContextCompat.getColor(this,
                    android.R.color.holo_green_dark));

            btnCheckout.setEnabled(true);
            btnCheckin.setEnabled(false);
            btnRoute.setEnabled(false);

            layoutCountdown.setVisibility(View.GONE);
            txtCountdown.clearAnimation();
            countdownHandler.removeCallbacksAndMessages(null);
        }
    }

    private void startCountdownIfNeeded() {

        countdownHandler.removeCallbacksAndMessages(null);

        if (!Utils.isCheckedOut(this)) {
            layoutCountdown.setVisibility(View.GONE);
            return;
        }

        Calendar deadline = Calendar.getInstance();
        deadline.add(Calendar.MINUTE, 15); // testing

        countdownRunnable = new Runnable() {
            @Override
            public void run() {

                long remainingMs = deadline.getTimeInMillis()
                        - System.currentTimeMillis();

                if (remainingMs <= 0) {

                    if (!timeOverHandled) {
                        timeOverHandled = true;
                        startAlertService();

                        Toast.makeText(MainActivity.this,
                                "🚨 Time over! Press SOS if you need help.",
                                Toast.LENGTH_LONG).show();
                    }

                    layoutCountdown.setVisibility(View.GONE);
                    txtCountdown.clearAnimation();
                    return;
                }

                long minutes = remainingMs / 60000;
                long seconds = (remainingMs / 1000) % 60;

                layoutCountdown.setVisibility(View.VISIBLE);
                txtCountdown.setText(
                        String.format("%02d:%02d", minutes, seconds));

                GradientDrawable bg =
                        (GradientDrawable) layoutCountdown.getBackground();

                if (remainingMs > 10 * 60 * 1000) {

                    bg.setColor(0xFF2E7D32);
                    txtCountdown.clearAnimation();

                } else if (remainingMs > 5 * 60 * 1000) {

                    bg.setColor(0xFFF9A825);
                    txtCountdown.clearAnimation();

                    if (!warned10) {
                        warned10 = true;
                        Toast.makeText(MainActivity.this,
                                "⚠️ 10 minutes remaining.",
                                Toast.LENGTH_SHORT).show();
                    }

                } else {

                    bg.setColor(0xFFC62828);

                    if (!warned5) {
                        warned5 = true;
                        startAlertService();
                        Toast.makeText(MainActivity.this,
                                "🚨 5 minutes remaining!",
                                Toast.LENGTH_LONG).show();
                    }

                    if (txtCountdown.getAnimation() == null) {
                        txtCountdown.startAnimation(pulseAnim);
                    }
                }

                countdownHandler.postDelayed(this, 1000);
            }
        };

        countdownHandler.post(countdownRunnable);
    }

    private void startAlertService() {
        Intent i = new Intent(this, AlertService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    void drawGeofence(GeoPoint center, int radiusMeters) {
        Polygon circle = new Polygon();
        circle.setPoints(Polygon.pointsAsCircle(center, radiusMeters));
        circle.setFillColor(0x121111FF);
        circle.setStrokeColor(0xFF0000FF);
        circle.setStrokeWidth(3f);
        map.getOverlays().add(circle);
    }
}
