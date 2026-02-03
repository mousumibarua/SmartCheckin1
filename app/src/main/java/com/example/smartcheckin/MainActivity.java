package com.example.smartcheckin;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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

public class MainActivity extends AppCompatActivity {

    /* ================= CONSTANTS ================= */

    private static final String CHANNEL_ID = "checkin_alerts";
    private static final int REQ_CAMERA = 100;
    private static final int REQ_PHOTO = 101;
    private static final int REQ_VIDEO = 102;
    private static final int REQ_SOS_PERMS = 200;

    private static final String EMERGENCY_NUMBER = "+919845115334";

    /* ================= MAP ================= */

    private MapView map;
    private MyLocationNewOverlay locationOverlay;
    private final GeoPoint campus = new GeoPoint(12.9716, 77.5946);

    /* ================= UI ================= */

    private Button btnCheckout, btnCheckin, btnSOS, btnRoute;
    private TextView txtStatus, txtCheckoutTime, txtCountdown;
    private LinearLayout layoutCountdown;

    /* ================= STATE ================= */

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private Animation pulseAnim;

    private boolean alert15Shown, alert10Shown, alert5Shown, violationShown;

    private FirebaseAnalytics firebaseAnalytics;

    // 🔥 SOS
    private String pendingSosReason;

    /* ================= MENU ================= */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.menu_logout) {
            logoutUser();
            return true;
        }

        if (item.getItemId() == R.id.menu_deregister) {
            deregisterUser();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /* ================= LIFECYCLE ================= */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

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
        btnCheckin.setOnClickListener(v -> handleCheckIn());
        btnRoute.setOnClickListener(v ->
                startActivity(new Intent(this, RouteActivity.class)));

        // 🔥 SOS ENTRY POINT
        btnSOS.setOnClickListener(v -> showSosMediaChoice());

        setupMap();
        updateUI();
    }
    private String getCurrentLocationUrl() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            return "Location permission not granted";
        }

        LocationManager lm =
                (LocationManager) getSystemService(LOCATION_SERVICE);

        if (lm == null) return "Location unavailable";

        Location location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (location == null) {
            return "Location unavailable";
        }

        return "https://www.openstreetmap.org/?mlat="
                + location.getLatitude()
                + "&mlon="
                + location.getLongitude();
    }

    /* ================= SOS FLOW ================= */

    private boolean ensureSosPermissions() {

        boolean smsGranted =
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.SEND_SMS)
                        == PackageManager.PERMISSION_GRANTED;

        boolean locGranted =
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        if (smsGranted && locGranted) return true;

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                REQ_SOS_PERMS
        );

        return false;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_SOS_PERMS) {
            if (allGranted(grantResults)) {
                sendSos();
            } else {
                toast("SMS & location permission required for SOS");
            }
        }
    }

    private void sendSos() {

        String locationText = "Location unavailable";

        try {
            LocationManager lm =
                    (LocationManager) getSystemService(LOCATION_SERVICE);

            if (lm != null &&
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED) {

                Location loc =
                        lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if (loc != null) {
                    locationText =
                            "https://www.openstreetmap.org/?mlat="
                                    + loc.getLatitude()
                                    + "&mlon=" + loc.getLongitude();
                }
            }
        } catch (Exception ignored) {}

        String sms =
                "🚨 SOS ALERT\n" +
                        "Reason: " + pendingSosReason +
                        "\nLocation:\n" + locationText;

        startActivity(new Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:" + EMERGENCY_NUMBER)
        ));

        try {
            SmsManager.getDefault().sendTextMessage(
                    EMERGENCY_NUMBER,
                    null,
                    sms,
                    null,
                    null
            );
        } catch (Exception e) {
            toast("Failed to send SMS");
        }

        toast("🚨 SOS sent successfully");
    }

    /* ================= MEDIA ================= */

    private void showSosMediaChoice() {

        String[] options = {"📸 Take Photo", "🎥 Record Video", "Skip"};

        new AlertDialog.Builder(this)
                .setTitle("Add media to SOS?")
                .setItems(options, (d, which) -> {
                    if (which == 0 && ensureCameraPermission()) {
                        startActivityForResult(
                                new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
                                REQ_PHOTO
                        );
                    } else if (which == 1 && ensureCameraPermission()) {
                        startActivityForResult(
                                new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE),
                                REQ_VIDEO
                        );
                    } else {
                        openSosChat();
                    }
                })
                .show();
    }

    private boolean ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) return true;

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                REQ_CAMERA
        );
        return false;
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);
        openSosChat(); // always return to SOS chat
    }

    private void openSosChat() {
        new SOSChatDialogFragment()
                .show(getSupportFragmentManager(), "SOS_CHAT");
    }

    /* ================= MAP ================= */

    private void setupMap() {
        Configuration.getInstance().setUserAgentValue(getPackageName());
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        locationOverlay =
                new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        locationOverlay.enableMyLocation();
        map.getOverlays().add(locationOverlay);

        map.getController().setZoom(15.0);
        map.getController().setCenter(campus);

        Marker marker = new Marker(map);
        marker.setPosition(campus);
        marker.setTitle("Campus");
        map.getOverlays().add(marker);

        drawGeofence(campus, 300);
    }

    /* ================= CHECK-IN ================= */

    private void startCheckoutFlow() {
        Utils.setCheckedOut(this, true);
        resetAlerts();
        updateUI();
        toast("Checked out! Monitoring started.");
    }

    private void handleCheckIn() {
        Utils.setCheckedOut(this, false);
        resetAlerts();
        updateUI();
        toast("Checked in successfully!");
    }

    private void resetAlerts() {
        alert15Shown = alert10Shown = alert5Shown = violationShown = false;
    }

    private void updateUI() {
        long checkoutTime = Utils.getCheckoutTime(this);

        if (Utils.isCheckedOut(this)) {
            txtStatus.setText("You are checked out");
            txtStatus.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_red_dark));

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

    /* ================= HELPERS ================= */
    private void startCountdown() {

        countdownHandler.removeCallbacksAndMessages(null);

        long checkoutTime = Utils.getCheckoutTime(this);
        long deadline = checkoutTime + (15 * 60 * 1000); // 15 minutes

        countdownRunnable = () -> {

            long remaining = deadline - System.currentTimeMillis();

            if (remaining <= 0) {
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
                bg.setColor(0xFF2E7D32); // green
            } else if (remaining > 5 * 60 * 1000) {
                bg.setColor(0xFFF9A825); // yellow
            } else {
                bg.setColor(0xFFC62828); // red
                if (txtCountdown.getAnimation() == null) {
                    txtCountdown.startAnimation(pulseAnim);
                }
            }

            countdownHandler.postDelayed(countdownRunnable, 1000);
        };

        countdownHandler.post(countdownRunnable);
    }
    private void sendSosViaSmsApp(String message) {

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + EMERGENCY_NUMBER));
        intent.putExtra("sms_body", message);

        startActivity(intent);
    }
    public void onSendSosClicked(String reason) {

        if (!ensureSosPermissions()) return;

        String locationUrl = getCurrentLocationUrl(); // your existing method

        String message =
                "🚨 SOS ALERT\n" +
                        "Reason: " + reason + "\n" +
                        "Location: " + locationUrl;

        sendSosViaSmsApp(message);
    }

    private boolean allGranted(int[] results) {
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void drawGeofence(GeoPoint center, int radiusMeters) {
        Polygon circle = new Polygon();
        circle.setPoints(Polygon.pointsAsCircle(center, radiusMeters));
        circle.setFillColor(0x121111FF);
        circle.setStrokeColor(0xFF0000FF);
        circle.setStrokeWidth(3f);
        map.getOverlays().add(circle);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Check-in Alerts",
                            NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    private void logoutUser() {
        startActivity(new Intent(this, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void deregisterUser() {
        getSharedPreferences("SmartCheckinPrefs", MODE_PRIVATE)
                .edit().clear().apply();

        startActivity(new Intent(this, RegisterActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
