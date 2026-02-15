package com.example.smartcheckin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapActivity extends AppCompatActivity implements RouteSelectionListener {

    private static final int REQ_LOCATION = 101;

    private MapView mapView;
    private ProgressBar routeLoader;
    private Marker userMarker;

    private double userLat, userLng;
    private float currentSpeed;

    private List<GeoPoint> campusPolygon;
    private final List<Polyline> routeOverlays = new ArrayList<>();

    private boolean navigationActive = false;
    private RouteScore activeRoute;
    private List<GeoPoint> activePath;
    private int currentPathIndex = 0;

    private AdaptiveOutputEngine adaptiveOutput;
    private TextToSpeech tts;

    private boolean awaitingModeResponse = false;
    private boolean modeDialogShown = false;
    private int modeIndex = 0;

    private Handler voiceHandler = new Handler(Looper.getMainLooper());
    private Handler noResponseHandler = new Handler(Looper.getMainLooper());
    private Runnable noResponseRunnable;

    private final String[] modeQuestions = {
            "Are you walking today?",
            "Are you using a wheelchair?",
            "Are you driving a four wheeler?",
            "Are you travelling by a two wheeler?",
            "Are you travelling by bus?"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.map);
        routeLoader = findViewById(R.id.routeLoader);

        adaptiveOutput = new AdaptiveOutputEngine(this);
        adaptiveOutput.setProfile(AppSettings.getProfile(this));

        tts = new TextToSpeech(this, status -> {});

        setupMap();
        campusPolygon = loadCampusPolygon();
        fetchCurrentLocation();

        Button btnStartNav = findViewById(R.id.btnStartNav);
        btnStartNav.setOnClickListener(v -> startNavigation());
    }

    private void setupMap() {
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(16.0);

        new Handler().postDelayed(() -> {
            if (!navigationActive) {
                askModeThenLoadRoutes();
            }
        }, 2000);
    }
    @Override
    public void onRouteSelected(RouteScore route) {

        activeRoute = route;
        activePath = route.route.path;

        clearRoutes();

        Polyline selected = new Polyline();
        selected.setPoints(route.route.path);
        selected.setColor(Color.GREEN);
        selected.setWidth(12f);

        mapView.getOverlays().add(selected);
        routeOverlays.add(selected);

        zoomToRoute(route.route.path);
        findViewById(R.id.btnStartNav).setVisibility(View.VISIBLE);

        adaptiveOutput.notifyTurn(
                "Route selected",
                route.riskLevel
        );

        if ("RED".equals(route.riskLevel)) {
            adaptiveOutput.notifyHighRisk();
        }

        mapView.invalidate();
    }
    private void startNavigation() {

        if (activeRoute == null) return;

        navigationActive = true;

        adaptiveOutput.notifyTurn(
                "Navigation started",
                activeRoute.riskLevel
        );

        Toast.makeText(this,
                "Navigation Started",
                Toast.LENGTH_SHORT).show();

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(this);

        client.requestLocationUpdates(
                com.google.android.gms.location.LocationRequest
                        .create()
                        .setInterval(3000)
                        .setPriority(
                                com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
                        ),
                locationCallback,
                getMainLooper()
        );
    }
    private void clearRoutes() {

        if (mapView == null) return;

        mapView.getOverlays().removeAll(routeOverlays);
        routeOverlays.clear();

        mapView.invalidate();
    }

    private void fetchCurrentLocation() {

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION
            );
            return;
        }

        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(this);

        client.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {

                Log.d("MAP_DEBUG", "Location received");

                userLat = location.getLatitude();
                userLng = location.getLongitude();
                currentSpeed = location.getSpeed();

                GeoPoint userPoint = new GeoPoint(userLat, userLng);
                mapView.getController().setCenter(userPoint);

                updateUserMarker(userLat, userLng);

                askModeThenLoadRoutes();
            }
        });
    }

    private void askModeThenLoadRoutes() {

        if (modeDialogShown) return;
        modeDialogShown = true;

        String[] modes = {
                "🚶 Walking",
                "♿ Wheelchair",
                "🚗 Driving",
                "🛵 Two Wheeler",
                "🚌 Bus"
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Choose transport mode")
                .setItems(modes, (dialog, which) -> {

                    RouteEngine.Mode selectedMode;

                    switch (which) {
                        case 0: selectedMode = RouteEngine.Mode.WALKING; break;
                        case 1: selectedMode = RouteEngine.Mode.WHEELCHAIR; break;
                        case 2: selectedMode = RouteEngine.Mode.DRIVING; break;
                        case 3: selectedMode = RouteEngine.Mode.TWO_WHEELER; break;
                        default: selectedMode = RouteEngine.Mode.BUS;
                    }

                    loadRoutesForMode(selectedMode);
                })
                .setCancelable(false)
                .show();

        if (adaptiveOutput != null) {
            adaptiveOutput.notifyTurn(
                    "Please select your mode of transport for today.",
                    "GREEN"
            );
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say yes or no");
        startActivityForResult(intent, 900);
    }

    private void askCurrentMode() {

        if (modeIndex >= modeQuestions.length) {
            askRepeatMenu();
            return;
        }

        awaitingModeResponse = true;

        adaptiveOutput.notifyTurn(
                "Please reply yes or no. " + modeQuestions[modeIndex],
                "GREEN"
        );

        startVoiceInput();
        startNoResponseTimer();
    }

    private void askRepeatMenu() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Repeat transport menu?")
                .setMessage("Do you want to hear transport options again?")
                .setPositiveButton("Yes", (d, w) -> askModeThenLoadRoutes())
                .setNegativeButton("No", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void startNoResponseTimer() {

        if (noResponseRunnable != null)
            noResponseHandler.removeCallbacks(noResponseRunnable);

        noResponseRunnable = () -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("No response detected")
                    .setMessage("Do you need SOS help?")
                    .setPositiveButton("Yes", (d, w) -> triggerSOS())
                    .setNegativeButton("No", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        };

        noResponseHandler.postDelayed(noResponseRunnable, 60000);
    }

    private void triggerSOS() {
        Toast.makeText(this, "SOS Triggered", Toast.LENGTH_LONG).show();
    }

    private void loadRoutesForMode(RouteEngine.Mode mode) {

        routeLoader.setVisibility(View.VISIBLE);

        GeoPoint gate1 = loadGate1();
        GeoPoint centroid = loadCampusCentroid();

        new Thread(() -> {
            try {

                List<RouteScore> candidates = new ArrayList<>();

                List<RouteEngine.Route> baseRoutes =
                        RouteEngine.fetchRoutes(
                                userLat,
                                userLng,
                                gate1,
                                mode
                        );

                for (RouteEngine.Route base : baseRoutes) {

                    double internalDistance =
                            gate1.distanceToAsDouble(centroid);

                    double internalTime = internalDistance / 1.4;
                    double roadEta = computeEtaForMode(base.distance, mode);
                    double totalEta = roadEta + internalTime;
                    double totalDistance = base.distance + internalDistance;

                    int risk =
                            RiskEngine.calculateRisk(
                                    totalEta,
                                    getTimeRemainingSeconds(),
                                    currentSpeed
                            );

                    RouteResult result = new RouteResult(
                            base.path,
                            totalDistance,
                            base.baseDuration,
                            totalEta,
                            mode,
                            "Primary"
                    );

                    RouteScore score =
                            RouteSafetyEngine.evaluateRoute(
                                    result,
                                    risk
                            );

                    candidates.add(score);
                }

                Collections.sort(
                        candidates,
                        (a, b) -> riskPriority(a.riskLevel)
                                - riskPriority(b.riskLevel)
                );

                runOnUiThread(() -> {

                    routeLoader.setVisibility(View.GONE);

                    RouteResultDialog dialog = new RouteResultDialog();
                    dialog.show(getSupportFragmentManager(), "ROUTES");

                    mapView.post(() ->
                            dialog.setRoutes(candidates)
                    );
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> routeLoader.setVisibility(View.GONE));
            }
        }).start();
    }

    private double computeEtaForMode(double distanceMeters, RouteEngine.Mode mode) {

        switch (mode) {
            case WHEELCHAIR: return distanceMeters / 1.0;
            case WALKING: return distanceMeters / 1.4;
            case BUS: return distanceMeters / 8.0;
            case TWO_WHEELER: return distanceMeters / 13.0;
            case DRIVING:
            default: return distanceMeters / 13.8;
        }
    }

    private double getTimeRemainingSeconds() {
        return 15 * 60;
    }

    private int riskPriority(String risk) {
        if ("GREEN".equals(risk)) return 0;
        if ("YELLOW".equals(risk)) return 1;
        return 2;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 900 && resultCode == RESULT_OK && data != null) {

            ArrayList<String> result =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (result == null || result.isEmpty()) return;

            String spoken = result.get(0).toLowerCase();

            if (awaitingModeResponse) {

                noResponseHandler.removeCallbacks(noResponseRunnable);

                if (spoken.matches(".*\\b(yes|yeah|yep|sure|ok)\\b.*")) {

                    awaitingModeResponse = false;

                    RouteEngine.Mode selectedMode;

                    switch (modeIndex) {
                        case 0: selectedMode = RouteEngine.Mode.WALKING; break;
                        case 1: selectedMode = RouteEngine.Mode.WHEELCHAIR; break;
                        case 2: selectedMode = RouteEngine.Mode.DRIVING; break;
                        case 3: selectedMode = RouteEngine.Mode.TWO_WHEELER; break;
                        default: selectedMode = RouteEngine.Mode.BUS;
                    }

                    tts.speak("Loading routes",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            null);

                    loadRoutesForMode(selectedMode);
                    return;
                }

                modeIndex++;

                new Handler().postDelayed(
                        this::askCurrentMode,
                        1200
                );
            }
        }
    }

    private void updateUserMarker(double lat, double lng) {

        GeoPoint userPoint = new GeoPoint(lat, lng);

        if (userMarker == null) {

            userMarker = new Marker(mapView);
            userMarker.setPosition(userPoint);
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            userMarker.setIcon(getResources().getDrawable(
                    org.osmdroid.library.R.drawable.person));

            mapView.getOverlays().add(userMarker);

        } else {
            userMarker.setPosition(userPoint);
        }

        mapView.invalidate();
    }

    private void zoomToRoute(List<GeoPoint> points) {

        if (points == null || points.isEmpty()) return;

        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for (GeoPoint p : points) {
            minLat = Math.min(minLat, p.getLatitude());
            maxLat = Math.max(maxLat, p.getLatitude());
            minLon = Math.min(minLon, p.getLongitude());
            maxLon = Math.max(maxLon, p.getLongitude());
        }

        BoundingBox box = new BoundingBox(maxLat, maxLon, minLat, minLon);
        mapView.zoomToBoundingBox(box, true, 120);
    }

    private List<GeoPoint> loadCampusPolygon() {
        List<GeoPoint> p = new ArrayList<>();
        p.add(new GeoPoint(12.9149, 77.6638));
        p.add(new GeoPoint(12.9159, 77.6638));
        p.add(new GeoPoint(12.9159, 77.6648));
        p.add(new GeoPoint(12.9149, 77.6648));
        return p;
    }

    private GeoPoint loadGate1() {
        return new GeoPoint(12.9152, 77.6635);
    }

    private GeoPoint loadCampusCentroid() {
        return GeoUtils.calculateCentroid(campusPolygon);
    }
    private void updateLiveEta(double lat, double lon, float speed) {

        if (activePath == null) return;

        double remainingDistance = 0;

        for (int i = currentPathIndex; i < activePath.size() - 1; i++) {
            remainingDistance +=
                    activePath.get(i)
                            .distanceToAsDouble(activePath.get(i + 1));
        }

        double etaSeconds;

        if (speed > 0.5f) {
            etaSeconds = remainingDistance / speed;
        } else {
            etaSeconds = remainingDistance / 1.4;
        }

        double etaMinutes = etaSeconds / 60.0;

        adaptiveOutput.notifyTurn(
                "ETA " + String.format("%.1f", etaMinutes) + " minutes",
                activeRoute != null ? activeRoute.riskLevel : "GREEN"
        );

        Toast.makeText(
                this,
                "Expected Time of arrival : " + String.format("%.1f", etaMinutes) + " mins",
                Toast.LENGTH_SHORT
        ).show();
    }

    private final com.google.android.gms.location.LocationCallback locationCallback =
            new com.google.android.gms.location.LocationCallback() {

                @Override
                public void onLocationResult(
                        com.google.android.gms.location.LocationResult result
                ) {

                    if (!navigationActive || result == null) return;

                    Location location = result.getLastLocation();
                    if (location == null) return;

                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    float speed = location.getSpeed();

                    updateUserMarker(lat, lon);
                    updateLiveEta(lat, lon, speed);
                }
            };

}
