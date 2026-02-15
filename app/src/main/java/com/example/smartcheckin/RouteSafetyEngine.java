package com.example.smartcheckin;

import java.util.Calendar;

public class RouteSafetyEngine {

    public static RouteScore evaluateRoute(
            RouteResult route,
            int latenessRisk
            // int index
    ) {

        RouteScore s = new RouteScore();

        s.route = route;
        s.distanceKm = route.distance / 1000.0;
        s.etaMinutes = route.adjustedDuration / 60.0;

        int lighting = isNight() ? 40 : 80;
        int crowd = 70;
        int traffic = route.mode == RouteEngine.Mode.WALKING ? 80 : 60;
        int timeRisk = Math.max(0, 100 - latenessRisk);

        double score =
                0.30 * lighting +
                        0.20 * crowd +
                        0.20 * traffic +
                        0.20 * timeRisk +
                        0.10 * (100 - s.distanceKm * 8);

        s.finalScore = clamp((int) score);
        s.riskLevel = classify(s.finalScore);

        s.routeName =
                getModeIcon(route.mode) +
                        " " + route.mode.name() +
                        " – " + route.variant;

        return s;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static String classify(int score) {
        if (score >= 75) return "GREEN";
        if (score >= 50) return "YELLOW";
        return "RED";
    }

    private static boolean isNight() {
        int hour = Calendar.getInstance()
                .get(Calendar.HOUR_OF_DAY);
        return hour >= 18 || hour < 6;
    }

    private static String getModeIcon(RouteEngine.Mode mode) {
        switch (mode) {
            case WALKING: return "🚶";
            case BUS: return "🚌";
            default: return "🚗";
        }
    }
}
