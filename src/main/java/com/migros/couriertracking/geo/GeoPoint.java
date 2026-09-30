package com.migros.couriertracking.geo;

public record GeoPoint(double lat, double lng) {

    public GeoPoint {
        if (Double.isNaN(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Latitude must be within [-90, 90]: " + lat);
        }
        if (Double.isNaN(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Longitude must be within [-180, 180]: " + lng);
        }
    }
}
