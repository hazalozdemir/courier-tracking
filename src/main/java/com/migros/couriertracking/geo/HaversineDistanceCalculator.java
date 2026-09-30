package com.migros.couriertracking.geo;

import org.springframework.stereotype.Component;

/**
 * Great-circle distance on a spherical earth. Error is below 0.5%, which is far smaller than
 * consumer GPS noise, so the extra precision of an ellipsoidal model is not worth its cost.
 */
@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    static final double EARTH_RADIUS_METERS = 6_371_008.8;

    @Override
    public double distanceMeters(GeoPoint from, GeoPoint to) {
        double dLat = Math.toRadians(to.lat() - from.lat());
        double dLng = Math.toRadians(to.lng() - from.lng());
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(from.lat())) * Math.cos(Math.toRadians(to.lat()))
                * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
