package com.example.smartcheckin;

import org.osmdroid.util.GeoPoint;
import java.util.List;

public class CampusBoundary {

    private String campusId;
    private List<GeoPoint> polygon;
    private double areaSqMeters;

    public CampusBoundary() {}

    public String getCampusId() {
        return campusId;
    }

    public void setCampusId(String campusId) {
        this.campusId = campusId;
    }

    public List<GeoPoint> getPolygon() {
        return polygon;
    }

    public void setPolygon(List<GeoPoint> polygon) {
        this.polygon = polygon;
    }

    public double getAreaSqMeters() {
        return areaSqMeters;
    }

    public void setAreaSqMeters(double areaSqMeters) {
        this.areaSqMeters = areaSqMeters;
    }
}
