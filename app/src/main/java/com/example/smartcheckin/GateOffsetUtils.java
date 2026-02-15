package com.example.smartcheckin;

import org.osmdroid.util.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates multiple approach points for the same gate
 * to force alternate routing geometries (K-path).
 */
public class GateOffsetUtils {

    // ~15 meters in lat/lng degrees
    private static final double OFFSET = 0.000135;

    public static List<GeoPoint> generateGateOffsets(GeoPoint gate) {

        List<GeoPoint> offsets = new ArrayList<>();

        // Center (original gate)
        offsets.add(gate);

        // Cardinal offsets (real, physical)
        offsets.add(new GeoPoint(
                gate.getLatitude() + OFFSET,
                gate.getLongitude()
        )); // North

        offsets.add(new GeoPoint(
                gate.getLatitude() - OFFSET,
                gate.getLongitude()
        )); // South

        offsets.add(new GeoPoint(
                gate.getLatitude(),
                gate.getLongitude() + OFFSET
        )); // East

        offsets.add(new GeoPoint(
                gate.getLatitude(),
                gate.getLongitude() - OFFSET
        )); // West

        return offsets;
    }
}
