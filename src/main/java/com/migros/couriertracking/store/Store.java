package com.migros.couriertracking.store;

import com.migros.couriertracking.geo.GeoPoint;

public record Store(String name, GeoPoint location) {
}
