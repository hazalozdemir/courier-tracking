package com.migros.couriertracking.event;

import com.migros.couriertracking.geo.GeoPoint;

import java.time.Instant;

public record StoreEntranceEvent(String courierId, String storeName, Instant enteredAt, GeoPoint location) {
}
