package com.example.smartcheckin;

public class RouteScore {

    public String routeName;     // e.g. "🚗 DRIVING – Fastest"
    public double distanceKm;
    public double etaMinutes;
    public int finalScore;       // 0–100
    public String riskLevel;     // GREEN / YELLOW / RED
    public RouteResult route;    // underlying route
}
