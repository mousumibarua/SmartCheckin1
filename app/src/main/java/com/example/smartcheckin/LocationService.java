package com.example.smartcheckin;

import android.util.Log;

public class LocationService {

    /* ================= CORE DEPENDENCIES ================= */

    private final CampusBoundary campusBoundary;
    private final UserState userState;

    /* ================= STATE ================= */

    private boolean lastInside = false;
    private long insideStartTime = 0;

    // Low visibility comes ONLY from MainActivity (sensor / demo)
    private boolean lowVisibility = false;

    /* ================= THRESHOLDS ================= */

    private static final int CHECKIN_THRESHOLD = 70;
    private static final int CHECKOUT_THRESHOLD = 30;
    private static final double BUFFER_METERS = 30.0;

    /* ================= CALLBACK ================= */

    public interface LocationCallback {
        void onAutoCheckIn(long time);
        void onAutoCheckOut(long time);
        void onOutsideCampus(long time);
    }

    private LocationCallback callback;

    public void setCallback(LocationCallback callback) {
        this.callback = callback;
    }

    /* ================= CONSTRUCTOR ================= */

    public LocationService(CampusBoundary campusBoundary, UserState userState) {
        this.campusBoundary = campusBoundary;
        this.userState = userState;
    }

    /* ================= EXTERNAL SIGNALS ================= */

    /**
     * Called by MainActivity when:
     * - light sensor detects darkness
     * - demo mode toggled
     */
    public void setLowVisibility(boolean lowVisibility) {
        this.lowVisibility = lowVisibility;
        Log.d("AI", "Low visibility set to: " + lowVisibility);
    }

    /* ================= LOCATION UPDATE ================= */

    public void onLocationUpdate(double lat, double lng) {

        long now = System.currentTimeMillis();

        boolean inside = GeoUtils.isPointInsidePolygon(
                lat, lng, campusBoundary.getPolygon()
        );

        double distance = GeoUtils.distanceToPolygon(
                lat, lng, campusBoundary.getPolygon()
        );

        int confidence = calculateConfidence(inside, distance, now);

        Log.d("LOCATION",
                "inside=" + inside +
                        " confidence=" + confidence +
                        " checkedIn=" + userState.isCheckedIn()
        );

        /* ================= OUTSIDE & NEVER CHECKED IN ================= */
        if (!inside && !userState.hasEverCheckedIn()) {
            if (callback != null) {
                callback.onOutsideCampus(now); // 🔥 NOW GUARANTEED
            }
            lastInside = false;
            return;
        }

        /* ================= AUTO CHECK-IN ================= */
        if (inside && confidence >= CHECKIN_THRESHOLD && !userState.isCheckedIn()) {

            userState.setCheckedIn(true);
            userState.setHasEverCheckedIn(true);
            userState.setLastCheckInTime(now);

            if (callback != null) {
                callback.onAutoCheckIn(now);
            }

            lastInside = true;
            return;
        }

        /* ================= AUTO CHECK-OUT ================= */
        if (!inside && userState.isCheckedIn()) {

            userState.setCheckedIn(false);
            userState.setLastCheckOutTime(now);

            if (callback != null) {
                callback.onAutoCheckOut(now);
            }

            lastInside = false;
            return;
        }

        lastInside = inside;
    }

    /* ================= CONFIDENCE ENGINE ================= */

    private int calculateConfidence(
            boolean inside,
            double distanceMeters,
            long now
    ) {

        int score = 0;

        // Geometry confidence
        if (inside) {
            score += 60;
        } else if (distanceMeters <= BUFFER_METERS) {
            score += 30;
        }

        // Dwell confidence
        if (inside && insideStartTime > 0) {
            long dwellSeconds = (now - insideStartTime) / 1000;
            if (dwellSeconds >= 15) {
                score += 10;
            }
        }

        // Contextual risk (sensor-driven)
        if (lowVisibility) {
            score += 15;
            Log.d("AI", "Low visibility risk applied");
        }

        return Math.min(score, 100);
    }
}
