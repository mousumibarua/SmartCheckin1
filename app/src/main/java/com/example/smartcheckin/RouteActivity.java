package com.example.smartcheckin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class RouteActivity extends AppCompatActivity {

    /* ================= CONFIG ================= */

    private static final long ETA_INTERVAL = 2 * 60 * 1000; // 2 min
    private static final double DESTINATION_RADIUS = 50;    // meters
    private static final int LOCATION_REQ = 101;

    /* ================= MAP & UI ================= */

    private MapView map;
    private Polyline routeLine;
    private Marker userMarker;
    private TextView txtEta;

    /* ================= LOCATION ================= */

    private GeoPoint campusLocation =
            new GeoPoint(12.95822, 79.14180); // destination
    private GeoPoint userLocation = null;

    private boolean hasRealGpsFix = false;

    private FusedLocationProviderClient fusedLocationClient;
    private Handler etaHandler;
    private double lastEtaMinutes = -1;

    /* ================= LIFECYCLE ================= */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route);

        // 🔐 Permission
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQ
            );
        }

        // 🔴 OSMDroid config (REQUIRED)
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(this)
        );
        Configuration.getInstance().setUserAgentValue("SmartCheckin/1.0");

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        // UI
        map = findViewById(R.id.map);
        txtEta = findViewById(R.id.txtEta);

       map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.0);
        map.getController().setCenter(campusLocation);

        addMarker(campusLocation, "Campus");

      /*  View backBtn = findViewById(R.id.btnBack);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());*/

        fetchUserLocationAndRoute();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startEtaUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopEtaUpdates();
    }

    @Override
    protected void onDestroy() {
        stopEtaUpdates();
        super.onDestroy();
    }

    /* ================= GPS ================= */

    private void fetchUserLocationAndRoute() {

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {

                    if (location == null) return;

                    userLocation = new GeoPoint(
                            location.getLatitude(),
                            location.getLongitude()
                    );

                    hasRealGpsFix = true; // ✅ VERY IMPORTANT

                    updateUserMarker(userLocation);
                    map.getController().animateTo(userLocation);

                    fetchRouteETA("walking");
                });
    }

    /* ================= ROUTING ================= */

    private void fetchRouteETA(String mode) {

        if (userLocation == null) return;

        new Thread(() -> {
            try {
                String urlStr =
                        "https://router.project-osrm.org/route/v1/" + mode + "/" +
                                userLocation.getLongitude() + "," + userLocation.getLatitude() + ";" +
                                campusLocation.getLongitude() + "," + campusLocation.getLatitude() +
                                "?overview=full&geometries=geojson";

                HttpURLConnection conn =
                        (HttpURLConnection) new URL(urlStr).openConnection();
                conn.connect();

                BufferedReader br =
                        new BufferedReader(new InputStreamReader(conn.getInputStream()));

                StringBuilder json = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) json.append(line);

                JSONObject route =
                        new JSONObject(json.toString())
                                .getJSONArray("routes")
                                .getJSONObject(0);

                parseRoute(route);

            } catch (Exception e) {
                Log.e("ROUTE_ERROR", "OSRM failed", e);
                runOnUiThread(this::openGoogleMapsFallback);
            }
        }).start();
    }

    private void parseRoute(JSONObject route) throws Exception {

        double durationSec = route.getDouble("duration");

        JSONObject geometry = route.getJSONObject("geometry");
        org.json.JSONArray coords = geometry.getJSONArray("coordinates");

        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < coords.length(); i++) {
            org.json.JSONArray c = coords.getJSONArray(i);
            points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
        }

        runOnUiThread(() -> {

            drawRoute(points);
            showETA(durationSec);

            // ✅ SAFE DESTINATION CHECK
            if (hasRealGpsFix &&
                    userLocation != null &&
                    isNearDestination(userLocation, campusLocation)) {

                onDestinationReached();
            }
        });
    }

    /* ================= MAP DRAW ================= */

    private void drawRoute(List<GeoPoint> points) {

        if (routeLine != null) {
            map.getOverlays().remove(routeLine);
        }

        routeLine = new Polyline();
        routeLine.setWidth(8f);
        routeLine.setColor(0xFF2E7D32);
        routeLine.setPoints(points);

        map.getOverlays().add(routeLine);
        map.invalidate();
    }

    private void updateUserMarker(GeoPoint point) {

        if (userMarker != null) {
            map.getOverlays().remove(userMarker);
        }

        userMarker = new Marker(map);
        userMarker.setPosition(point);
        userMarker.setTitle("You");
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        map.getOverlays().add(userMarker);
        map.invalidate();
    }

    private void addMarker(GeoPoint point, String title) {
        Marker m = new Marker(map);
        m.setPosition(point);
        m.setTitle(title);
        map.getOverlays().add(m);
    }

    /* ================= ETA ================= */

    private void showETA(double durationSec) {

        double minutes = durationSec / 60;

        txtEta.setText(
                "ETA: " + String.format("%.1f", minutes) + " mins"
        );

        lastEtaMinutes = minutes;
    }

    private void startEtaUpdates() {

        if (etaHandler != null) return;

        etaHandler = new Handler(Looper.getMainLooper());
        etaHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fetchUserLocationAndRoute();
                etaHandler.postDelayed(this, ETA_INTERVAL);
            }
        }, ETA_INTERVAL);
    }

    private void stopEtaUpdates() {
        if (etaHandler != null) {
            etaHandler.removeCallbacksAndMessages(null);
            etaHandler = null;
        }
    }

    /* ================= DESTINATION ================= */

    private boolean isNearDestination(GeoPoint user, GeoPoint dest) {

        float[] results = new float[1];
        Location.distanceBetween(
                user.getLatitude(), user.getLongitude(),
                dest.getLatitude(), dest.getLongitude(),
                results
        );
        return results[0] <= DESTINATION_RADIUS;
    }

    private void onDestinationReached() {

        stopEtaUpdates();

        Toast.makeText(
                this,
                "🎉 Destination reached",
                Toast.LENGTH_LONG
        ).show();
    }

    /* ================= FALLBACK ================= */

    private void openGoogleMapsFallback() {

        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=" +
                        campusLocation.getLatitude() + "," +
                        campusLocation.getLongitude() +
                        "&mode=w"));

        intent.setPackage("com.google.android.apps.maps");
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQ &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            fetchUserLocationAndRoute();
        }
    }
}
