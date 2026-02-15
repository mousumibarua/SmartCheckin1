package com.example.smartcheckin;

import org.osmdroid.util.GeoPoint;
import java.util.List;

public class RouteResult {

    public List<GeoPoint> path;
    public double distance;           // meters
    public double baseDuration;        // seconds (OSRM)
    public double adjustedDuration;    // seconds (variant-adjusted ETA)
    public RouteEngine.Mode mode;      // WALKING / DRIVING / BUS
    public String variant;             // Fastest / Balanced / Safer / Reliable

    public RouteResult(
            List<GeoPoint> path,
            double distance,
            double baseDuration,
            double adjustedDuration,
            RouteEngine.Mode mode,
            String variant
    ) {
        this.path = path;
        this.distance = distance;
        this.baseDuration = baseDuration;
        this.adjustedDuration = adjustedDuration;
        this.mode = mode;
        this.variant = variant;
    }
    // ✅ New constructor (USED NOW)
    public RouteResult(
            List<GeoPoint> path,
            double distance,
            double baseDuration,
            double adjustedDuration,
            RouteEngine.Mode mode
    ) {
        this.path = path;
        this.distance = distance;
        this.baseDuration = baseDuration;
        this.adjustedDuration = adjustedDuration;
        this.mode = mode;
        this.variant = null;
    }
}
