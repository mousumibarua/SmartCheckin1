package com.example.smartcheckin;

import org.osmdroid.util.GeoPoint;
import java.util.List;

/**
 * Identifies unsafe segments on a route and
 * checks whether a route passes through them.
 */
public class BlockedSegmentUtils {

    /**
     * Extracts the most risky segment (midpoint)
     * from a route polyline.
     */
    public static GeoPoint findRiskyPoint(List<GeoPoint> path) {
        if (path == null || path.size() < 3) return null;

        int mid = path.size() / 2;
        return path.get(mid);
    }

    /**
     * Checks if a route passes near a blocked point.
     */
    public static boolean intersectsBlockedArea(
            List<GeoPoint> route,
            GeoPoint blocked,
            double radiusMeters
    ) {
        if (blocked == null) return false;

        for (GeoPoint p : route) {
            if (p.distanceToAsDouble(blocked) <= radiusMeters) {
                return true;
            }
        }
        return false;
    }
}
