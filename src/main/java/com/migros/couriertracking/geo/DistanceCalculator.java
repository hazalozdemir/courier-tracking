package com.migros.couriertracking.geo;

/**
 * Strategy for measuring the distance between two coordinates. Swappable so that a cheaper
 * approximation (equirectangular) or a more precise one (Vincenty) can be plugged in without
 * touching business logic.
 */
public interface DistanceCalculator {

    /** @return distance in meters */
    double distanceMeters(GeoPoint from, GeoPoint to);
}
