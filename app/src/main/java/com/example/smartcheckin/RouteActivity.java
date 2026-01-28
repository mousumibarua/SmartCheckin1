package com.example.smartcheckin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.osmdroid.config.Configuration;
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

    private MapView map;

    private GeoPoint userLocation =
            new GeoPoint(13.0827, 80.2707);
    private GeoPoint campusLocation =
            new GeoPoint(13.0900, 80.2800);

    private Polyline animatedRoute;
    private int animIndex = 0;
    private Handler routeAnimHandler;
    private boolean isAnimating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route);

        View backBtn = findViewById(R.id.btnBack);
        if (backBtn != null)backBtn.setOnClickListener(v -> {
            stopRouteAnimation();
            finish();
        });

        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(this)
        );

        map = findViewById(R.id.map);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);
        map.getController().setCenter(userLocation);

        addMarker(userLocation, "You are here");
        addMarker(campusLocation, "Campus");

        fetchRouteETA("walking");
    }

    /* ---------------- ADD MAP MARKER ---------------- */
    private void addMarker(GeoPoint point, String title) {
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setTitle(title);
        map.getOverlays().add(marker);
    }

    /* ---------------- FETCH ROUTE ---------------- */
    private void fetchRouteETA(String mode) {

        new Thread(() -> {
            try {
                String urlStr =
                        "https://router.project-osrm.org/route/v1/" + mode + "/" +
                                userLocation.getLongitude() + "," + userLocation.getLatitude() + ";" +
                                campusLocation.getLongitude() + "," + campusLocation.getLatitude() +
                                "?overview=full&geometries=geojson";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                BufferedReader br =
                        new BufferedReader(new InputStreamReader(conn.getInputStream()));

                StringBuilder json = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) json.append(line);

                JSONObject response = new JSONObject(json.toString());
                JSONObject route = response.getJSONArray("routes").getJSONObject(0);

                parseRoute(route);

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Unable to fetch route",
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /* ---------------- PARSE ROUTE ---------------- */
    private void parseRoute(JSONObject route) throws Exception {

        double durationSec = route.getDouble("duration");
        double distanceM = route.getDouble("distance");

        int routeColor = getRouteColor(distanceM, durationSec);

        JSONObject geometry = route.getJSONObject("geometry");
        org.json.JSONArray coords = geometry.getJSONArray("coordinates");

        List<GeoPoint> points = new ArrayList<>();

        for (int i = 0; i < coords.length(); i++) {
            org.json.JSONArray c = coords.getJSONArray(i);
            points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
        }

        runOnUiThread(() -> {

            if (points.size() < 2) {
                Toast.makeText(
                        this,
                        "Route data unavailable",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            animateRoute(points, routeColor);
            showETA(durationSec, distanceM);
        });
    }

    /* ---------------- ANIMATE ROUTE (CRASH-PROOF) ---------------- */
    private void animateRoute(List<GeoPoint> points, int routeColor) {

        if (points == null || points.size() < 2) return;

        // Stop any previous animation
        stopRouteAnimation();

        if (animatedRoute != null) {
            map.getOverlays().remove(animatedRoute);
        }

        animatedRoute = new Polyline();
        animatedRoute.setWidth(9f);
        animatedRoute.setColor(routeColor);
        map.getOverlays().add(animatedRoute);

        List<GeoPoint> drawPoints = new ArrayList<>();
        routeAnimHandler = new Handler(Looper.getMainLooper());
        animIndex = 0;
        isAnimating = true;

        routeAnimHandler.post(new Runnable() {
            @Override
            public void run() {

                // 🛑 STOP CONDITIONS (CRITICAL)
                if (!isAnimating || map == null || animatedRoute == null) {
                    return;
                }

                if (animIndex >= points.size()) {
                    isAnimating = false;
                    return;
                }

                drawPoints.add(points.get(animIndex));

                if (drawPoints.size() >= 2) {
                    animatedRoute.setPoints(drawPoints);
                    map.invalidate();
                }

                animIndex++;
                routeAnimHandler.postDelayed(this, 25);
            }
        });
    }

    private void stopRouteAnimation() {

        isAnimating = false;

        if (routeAnimHandler != null) {
            routeAnimHandler.removeCallbacksAndMessages(null);
            routeAnimHandler = null;
        }
    }
    /* ---------------- SHOW ETA ---------------- */
    private void showETA(double durationSec, double distanceM) {

        double minutes = durationSec / 60;
        double km = distanceM / 1000;

        Toast.makeText(
                this,
                "Shortest route found\n" +
                        "ETA: " + String.format("%.1f", minutes) + " mins\n" +
                        "Distance: " + String.format("%.2f", km) + " km",
                Toast.LENGTH_LONG
        ).show();
    }

    private int getRouteColor(double distanceM, double durationSec) {

        double speed = distanceM / durationSec;
        Log.d("ROUTE", "Avg speed = " + speed);

        if (speed <= 8) {
            return 0xFFFF0000; // red
        } else {
            return 0xFF2E7D32; // green
        }
    }
    @Override
    protected void onDestroy() {
        stopRouteAnimation();
        super.onDestroy();
    }
}
