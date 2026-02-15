package com.example.smartcheckin;

import org.osmdroid.util.GeoPoint;
import java.util.List;

/**
 * Utility class for geographic calculations.
 * - Point in polygon (ray-casting)
 * - Distance estimation
 * - Polygon centroid calculation (for routing)
 */
public class GeoUtils {

    /**
     * Checks whether a point lies inside a polygon
     * using ray-casting algorithm.
     */
    public static boolean isPointInsidePolygon(
            double lat,
            double lng,
            List<GeoPoint> polygon) {

        if (polygon == null || polygon.size() < 3) {
            return false;
        }

        boolean inside = false;
        int j = polygon.size() - 1;

        for (int i = 0; i < polygon.size(); i++) {

            double xi = polygon.get(i).getLatitude();
            double yi = polygon.get(i).getLongitude();

            double xj = polygon.get(j).getLatitude();
            double yj = polygon.get(j).getLongitude();

            boolean intersect =
                    ((yi > lng) != (yj > lng)) &&
                            (lat < (xj - xi) * (lng - yi) / (yj - yi) + xi);

            if (intersect) {
                inside = !inside;
            }
            j = i;
        }

        return inside;
    }

    /**
     * Returns minimum distance (in meters)
     * from a point to polygon vertices.
     * Used for proximity / warning logic.
     */
    public static double distanceToPolygon(
            double lat,
            double lng,
            List<GeoPoint> polygon) {

        if (polygon == null || polygon.isEmpty()) {
            return Double.MAX_VALUE;
        }

        double min = Double.MAX_VALUE;
        GeoPoint p = new GeoPoint(lat, lng);

        for (GeoPoint gp : polygon) {
            min = Math.min(min, p.distanceToAsDouble(gp));
        }
        return min;
    }

    /**
     * Calculates centroid of a polygon.
     * REQUIRED for routing engines (ORS / OSRM),
     * since they cannot route to polygons.
     */
    public static GeoPoint calculateCentroid(List<GeoPoint> polygon) {

        if (polygon == null || polygon.isEmpty()) {
            return null;
        }

        double latSum = 0;
        double lonSum = 0;

        for (GeoPoint p : polygon) {
            latSum += p.getLatitude();
            lonSum += p.getLongitude();
        }

        return new GeoPoint(
                latSum / polygon.size(),
                lonSum / polygon.size()
        );
    }
}
