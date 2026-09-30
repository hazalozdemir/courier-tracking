package com.migros.couriertracking.courier;

import com.migros.couriertracking.geo.GeoPoint;

import java.time.Instant;
import java.util.Objects;

/** A single streamed geolocation of a courier. */
public record LocationUpdate(String courierId, Instant time, GeoPoint location) {

    public LocationUpdate {
        Objects.requireNonNull(courierId, "courierId");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(location, "location");
    }
}
