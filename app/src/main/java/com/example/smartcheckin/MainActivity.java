package com.example.smartcheckin;

import static com.example.smartcheckin.VoiceEngine.speak;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import android.speech.tts.TextToSpeech;
import android.os.Vibrator;
import android.os.VibrationEffect;
import java.util.Locale;
import android.provider.Settings;
import android.speech.tts.UtteranceProgressListener;


public class MainActivity extends AppCompatActivity {

    /* ================= MAP ================= */
    private MapView map;
    private MyLocationNewOverlay myLocationOverlay;   // 👤 ADDED
    private TextView txtStatus;
    private Button btnRoute;

    /* ================= CAMPUS POLYGON ================= */
    private final List<GeoPoint> campusPolygon = new ArrayList<>();

    /* ================= SERVICES ================= */
    private LocationService locationService;
    private LocationManager locationManager;
    private static final int REQ_CAMERA = 201;

    /* ================= CAMERA DARKNESS ================= */
    private CameraLightDetector cameraLightDetector;
    private String lastStatusText = "";
    private enum StatusType { CHECKED_IN, CHECKED_OUT, OUTSIDE }
    private StatusType currentStatus = StatusType.OUTSIDE;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private Vibrator vibrator;
    private boolean ttsReady = false;
    private boolean permissionRequested  = false;
    private int permissionAttempts = 0;
    private static final int MAX_ATTEMPTS = 3;
    private boolean voiceModeEnabled = false;
    private AlertDialog profileDialog;
    private boolean awaitingNavigationResponse = false;
    private Handler responseHandler = new Handler(Looper.getMainLooper());
    private boolean profileCompleted = false;
    private boolean awaitingProfileResponse = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // ✅ Initialize TTS FIRST

        tts = new TextToSpeech(this, status -> {

            ttsReady = true;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

                @Override public void onStart(String id) {}

                @Override
                public void onDone(String id) {

                    runOnUiThread(() -> {

                        switch (id) {

                            case "WELCOME_MSG":
                                requestLocationPermission();
                                break;

                            case "PROFILE_MSG":
                                showProfileDialog();
                                new Handler(Looper.getMainLooper()).postDelayed(
                                        () -> startVoiceSelection(),
                                        1200
                                );
                                break;

                            case "NAV_PROMPT":
                                startVoiceSelection();
                                break;

                            case "OUTSIDE_MSG":
                                askNavigationAfterDelay();
                                break;
                        }
                    });
                }

                @Override public void onError(String id) {}
            });

            if (AppSettings.isFirstLaunch(MainActivity.this)) {

                tts.speak(
                        "Welcome to Smart Check-in. Location permission is required. After this message, the permission screen will open. Please press Allow.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "WELCOME_MSG"
                );

            } else {
                requestLocationPermission();
            }
        });



        Configuration.getInstance().load(
                getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE)
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setupPreviewMap();

        txtStatus = findViewById(R.id.txtStatus);
        btnRoute = findViewById(R.id.btnRoute);

        btnRoute.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(intent);
        });

        // 🚫 DO NOT start location yet (permission not granted)
        // initLocationService();
        // startLocationUpdates();

        showInitialOutsideStatus();
    }

    private void startFirstLaunchFlow() {

        if (!ttsReady) return;

        if (AppSettings.isFirstLaunch(this)) {

            tts.speak(
                    "Welcome to Smart Check-in. Location permission is required. " +
                            "After this message, the permission screen will open. Please press Allow.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "WELCOME_MSG"
            );

        } else {
            requestLocationPermission();
        }
    }


    private void requestLocationPermission() {
        //  if (permissionRequested) return;   // ✅ PREVENT DOUBLE CALL

        //   permissionRequested = true;

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED) {
            enableLocationFeatures();
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQ_LOCATION
        );
    }

    private static final int REQ_LOCATION = 301;

    /* ===================================================== */
    /* 🔵 PREVIEW MAP WITH LIVE USER LOCATION               */
    /* ===================================================== */

    private void setupPreviewMap() {

        map = findViewById(R.id.map);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);

        map.getController().setZoom(17.0);
        map.getController().setCenter(
                new GeoPoint(12.914976, 77.663804)
        );

        // 👤 LIVE USER LOCATION OVERLAY
        myLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this),
                map
        );

        //  myLocationOverlay.enableMyLocation();
        //   myLocationOverlay.enableFollowLocation();

        map.getOverlays().add(myLocationOverlay);
    }
    private void enableLocationFeatures() {

        if (myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();
            myLocationOverlay.enableFollowLocation();
        }

        initLocationService();
        startLocationUpdates();
    }


    /* ===================================================== */
    /* CAMERA PERMISSION + LIGHT DETECTION                  */
    /* ===================================================== */

    private void requestCameraPermissionIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQ_CAMERA
            );
        } else {
            initLightDetection();
        }
    }

    private void initLightDetection() {

        if (cameraLightDetector != null) return;

        cameraLightDetector = new CameraLightDetector(this,
                new CameraLightDetector.LightCallback() {

                    @Override
                    public void onDarkDetected() {
                        runOnUiThread(() -> {
                            txtStatus.setText("⚠ Low visibility detected");
                            txtStatus.postDelayed(
                                    () -> txtStatus.setText(lastStatusText),
                                    7000
                            );
                        });
                    }

                    @Override
                    public void onLightDetected() {
                        runOnUiThread(() ->
                                txtStatus.setText(lastStatusText)
                        );
                    }
                });

        cameraLightDetector.start();
    }

    /* ===================================================== */
    /* LOCATION SERVICE                                     */
    /* ===================================================== */

    private void initLocationService() {

        CampusBoundary boundary = new CampusBoundary();
        boundary.setPolygon(campusPolygon);

        UserState userState = new UserState();
        locationService = new LocationService(boundary, userState);

        locationService.setCallback(new LocationService.LocationCallback() {

            @Override
            public void onAutoCheckIn(long time) {
                runOnUiThread(() -> {
                    currentStatus = StatusType.CHECKED_IN;
                    lastStatusText = "Auto checked IN at\n" +
                            DateFormat.format("dd MMM yyyy, hh:mm a", time);
                    txtStatus.setText(lastStatusText);
                    btnRoute.setEnabled(false);
                });
            }


            @Override
            public void onAutoCheckOut(long time) {
                runOnUiThread(() -> {
                    currentStatus = StatusType.CHECKED_OUT;
                    lastStatusText = "Auto checked OUT at\n" +
                            DateFormat.format("dd MMM yyyy, hh:mm a", time);
                    txtStatus.setText(lastStatusText);
                    enableNavigateBack();
                });
            }

            @Override
            public void onOutsideCampus(long time) {
                runOnUiThread(() -> {

                    currentStatus = StatusType.OUTSIDE;
                    lastStatusText = "You are currently outside campus";
                    txtStatus.setText(lastStatusText);

                    enableNavigateBack();
                    Log.d("VOICE_CHECK","voiceModeEnabled="+voiceModeEnabled);

                    if (voiceModeEnabled && ttsReady) {

                      /*  tts.speak(lastStatusText,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "OUTSIDE_MSG");*/
                      /*  new Handler(Looper.getMainLooper()).postDelayed(
                                () -> askNavigationAfterDelay(),
                                5000   // wait for speech to finish
                        );*/

                    }
                });
            }

        });
    }

    private void startLocationUpdates() {

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                3000,
                2,
                location -> {
                    if (locationService != null) {
                        locationService.onLocationUpdate(
                                location.getLatitude(),
                                location.getLongitude()
                        );
                    }
                }
        );
    }

    private void showInitialOutsideStatus() {
        currentStatus = StatusType.OUTSIDE;
        lastStatusText = "You are currently outside campus";
        txtStatus.setText(lastStatusText);
        enableNavigateBack();
    }

    private void enableNavigateBack() {
        if (btnRoute == null) return;
        btnRoute.setVisibility(View.VISIBLE);
        btnRoute.setEnabled(true);
        btnRoute.setAlpha(1.0f);
    }
    public void onSendSosClicked(String reason) {
        Toast.makeText(this,
                "SOS triggered: " + reason,
                Toast.LENGTH_LONG).show();

        Log.d("SOS_EVENT", "Reason: " + reason);

        // 🔴 TODO: Add real SOS logic here later
        // Example:
        // sendEmergencySMS(reason);
        // notifyServer(reason);
    }

    public void navigateUsingSelectedRoute(RouteScore route) {
        if (route == null) return;

        GeoPoint campusGate = campusPolygon.get(0);

        Uri uri = Uri.parse(
                "google.navigation:q=" +
                        campusGate.getLatitude() + "," +
                        campusGate.getLongitude() +
                        "&mode=w"
        );

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        startActivity(intent);
    }
    private void askAccessibilityProfile() {

        if (!ttsReady) return;
        awaitingProfileResponse = true;

        tts.speak(
                "Choose your preferred interaction style. " +
                        "If you need voice assistance say Yes. " +
                        "Otherwise tap the screen.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "PROFILE_MSG"
        );


    }





    private void showProfileDialog(){

        String[] options = {
                "Standard Experience",
                "Voice Assisted Mode",
                "Visual Priority Mode",
                "Text Communication Mode",
                "Multi-Sensory Mode"
        };

        profileDialog = new AlertDialog.Builder(this)
                .setTitle("Select Interaction Style")
                .setItems(options, (dialog, which) -> {

                    AccessibilityProfile profile;

                    switch (which) {
                        case 1: profile = AccessibilityProfile.BLIND; break;
                        case 2: profile = AccessibilityProfile.DEAF; break;
                        case 3: profile = AccessibilityProfile.MUTE; break;
                        case 4: profile = AccessibilityProfile.DEAF_BLIND; break;
                        default: profile = AccessibilityProfile.STANDARD;
                    }

                    AppSettings.saveProfile(this, profile);
                    AppSettings.setFirstLaunchCompleted(this); // ⭐ IMPORTANT

                    Toast.makeText(this,"Interaction style saved",Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .create();

        profileDialog.show();
    }

    private void startVoiceSelection() {

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Say Yes..");

        startActivityForResult(intent, 500);
    }
    private void safeSpeak(String text, String id) {
        if (tts == null || !ttsReady) {
            Log.w("TTS", "TTS not ready - skipping: " + text);
            return;
        }
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        } catch (Exception e) {
            Log.e("TTS", "speak failed: " + e.getMessage());
        }
    }
    private void speakCurrentStatusWithDelay() {

        if (!voiceModeEnabled || tts == null || !ttsReady) return;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            if (lastStatusText != null && !lastStatusText.isEmpty()) {
                tts.speak(lastStatusText,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "STATUS_READ");
            }

        }, 4000);
        // ⭐ AFTER status → ask navigation
        new Handler(Looper.getMainLooper()).postDelayed(
                this::askNavigationAfterDelay,
                9000   // 4s delay + speech time buffer
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQ_LOCATION) return;

        if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            permissionAttempts = 0;

            if (tts != null)
                tts.speak("Thank you. Location permission granted.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null);

            enableLocationFeatures();
            if (AppSettings.isFirstLaunch(this)) {
                askAccessibilityProfile();
            }
            return;
        }

        // ❌ DENIED
        permissionAttempts++;

        if (permissionAttempts < MAX_ATTEMPTS) {

            if (tts != null)
                tts.speak(
                        "Location permission denied. The screen is still open. Please press Allow.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                );

            new Handler(Looper.getMainLooper()).postDelayed(
                    this::requestLocationPermission,
                    2500
            );

        } else {

            // Detect Don't Ask Again
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                if (tts != null)
                    tts.speak(
                            "Permission permanently denied. Opening settings. Please enable location permission there.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            null
                    );

                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);

            } else {

                if (tts != null)
                    tts.speak(
                            "Permission denied multiple times. Please restart app and allow permission.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            null
                    );
            }
        }
    }

    private void askNavigationAfterDelay() {

        if (!voiceModeEnabled || tts == null || !ttsReady) return;

        awaitingNavigationResponse = true;   // ⭐ MISSING LINE — REQUIRED
        awaitingProfileResponse = false;
        profileCompleted = true;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            tts.speak(
                    "Do you want to navigate back to campus? Say yes or no.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "NAV_PROMPT"
            );


            // timeout safety
            responseHandler.postDelayed(() -> {

                if (awaitingNavigationResponse) {

                    awaitingNavigationResponse = false;

                    tts.speak(
                            "No response received. Navigation cancelled.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            null
                    );
                }

            }, 30000);

        }, 10000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 500 && resultCode == RESULT_OK && data != null) {

            ArrayList<String> result =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (result == null || result.isEmpty()) return;

            String spoken = result.get(0).toLowerCase();

        /* =====================================================
           1️⃣ NAVIGATION RESPONSE MODE (highest priority)
           ===================================================== */

            if (awaitingNavigationResponse) {

                awaitingNavigationResponse = false;
                responseHandler.removeCallbacksAndMessages(null);

                if (spoken.matches(".*\\b(yes|yeah|yep|sure|ok)\\b.*")) {

                    awaitingNavigationResponse = false;
                    openNavigationScreen();
                    //  navigateUsingSelectedRoute(null);

                } else {

                    tts.speak(
                            "Transport mode not selected yet",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            null
                    );
                }

                return;
            }

        /* =====================================================
           2️⃣ PROFILE SELECTION MODE
           ===================================================== */
            if (profileCompleted) return;
            AccessibilityProfile profile = AccessibilityProfile.STANDARD;

            if (spoken.matches(".*\\b(yes|yeah|yep|sure|ok)\\b.*")) {
                profile = AccessibilityProfile.BLIND;
                voiceModeEnabled = true;
            }
            else if (spoken.matches(".*\\b(no|nope|nah)\\b.*")) {
                profile = AccessibilityProfile.STANDARD;
                voiceModeEnabled = false;
            }
            else if (spoken.contains("visual")) {
                profile = AccessibilityProfile.DEAF;
            }
            else if (spoken.contains("text")) {
                profile = AccessibilityProfile.MUTE;
            }
            else if (spoken.contains("multi")) {
                profile = AccessibilityProfile.DEAF_BLIND;
            }
            else if (spoken.contains("standard")) {
                voiceModeEnabled = false;
            }

            /* Save profile */
            AppSettings.saveProfile(this, profile);
            AppSettings.setFirstLaunchCompleted(this);
            profileCompleted = true;

            /* Close dialog if visible */
            if (profileDialog != null && profileDialog.isShowing()) {
                profileDialog.dismiss();
            }

            Toast.makeText(this,
                    "Interaction style saved",
                    Toast.LENGTH_SHORT).show();

            /* Voice confirmation */
            if (voiceModeEnabled) {

                speak(this,
                        "Interaction style accepted. Creating your profile.");

                new Handler(Looper.getMainLooper()).postDelayed(
                        this::speakCurrentStatusWithDelay,
                        3000
                );
            }
        }
    }


    private void openNavigationScreen() {

        Intent intent = new Intent(MainActivity.this, MapActivity.class);
        startActivity(intent);
    }


    /* ===================================================== */
    /* LIFECYCLE                                            */
    /* ===================================================== */


    @Override
    protected void onResume() {

        super.onResume();
        if (map != null) map.onResume();
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cameraLightDetector != null) {
            cameraLightDetector.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraLightDetector != null) {
            cameraLightDetector.stop();
            cameraLightDetector = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
