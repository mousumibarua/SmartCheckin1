package com.example.smartcheckin;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RouteEngine {

    public enum Mode {
        WALKING("foot"),
        WHEELCHAIR("wheelchair"),
        DRIVING("driving"),
        BUS("driving"),
        TWO_WHEELER("driving");

        final String osrmProfile;

        Mode(String profile) {
            this.osrmProfile = profile;
        }
    }

    public static class Route {
        public List<GeoPoint> path;
        public double distance;
        public double baseDuration;
    }

    /**
     * REAL K-PATH:
     * Multiple destination points → multiple route geometries
     */
    public static List<Route> fetchRoutes(
            double srcLat, double srcLng,
            GeoPoint gate,
            Mode mode
    ) throws Exception {

        List<Route> results = new ArrayList<>();
        Set<String> geometryHashes = new HashSet<>();

        List<GeoPoint> gateVariants =
                GateOffsetUtils.generateGateOffsets(gate);

        for (GeoPoint dst : gateVariants) {

            String urlStr =
                    "https://router.project-osrm.org/route/v1/" +
                            mode.osrmProfile + "/" +
                            srcLng + "," + srcLat + ";" +
                            dst.getLongitude() + "," + dst.getLatitude() +
                            "?overview=full&geometries=geojson";

            URL url = new URL(urlStr);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            );

            StringBuilder json = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
                json.append(line);

            JSONObject root =
                    new JSONObject(json.toString());

            JSONArray routesJson =
                    root.getJSONArray("routes");

            if (routesJson.length() == 0) continue;

            JSONObject r = routesJson.getJSONObject(0);

            Route route = new Route();
            route.distance = r.getDouble("distance");
            route.baseDuration = r.getDouble("duration");
            route.path = decodeGeometry(
                    r.getJSONObject("geometry")
            );

            // 🔑 Deduplicate geometries
            String hash = geometryHash(route.path);
            if (!geometryHashes.contains(hash)) {
                geometryHashes.add(hash);
                results.add(route);
            }
        }

        return results;
    }

    /* ---------------- HELPERS ---------------- */

    private static List<GeoPoint> decodeGeometry(
            JSONObject geometry
    ) throws Exception {

        List<GeoPoint> points = new ArrayList<>();
        JSONArray coords =
                geometry.getJSONArray("coordinates");

        for (int i = 0; i < coords.length(); i++) {
            JSONArray p = coords.getJSONArray(i);
            points.add(new GeoPoint(
                    p.getDouble(1),
                    p.getDouble(0)
            ));
        }
        return points;
    }

    private static String geometryHash(
            List<GeoPoint> path
    ) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i += 5) {
            GeoPoint p = path.get(i);
            sb.append(
                    String.format("%.5f,%.5f|",
                            p.getLatitude(),
                            p.getLongitude())
            );
        }
        return sb.toString();
    }
}
