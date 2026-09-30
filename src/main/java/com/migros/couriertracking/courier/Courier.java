package com.migros.couriertracking.courier;

import com.migros.couriertracking.geo.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Aggregate holding a courier's running state. Storing the running total (instead of summing
 * every historical ping on read) keeps {@code getTotalTravelDistance} O(1).
 */
@Entity
@Table(name = "courier")
public class Courier {

    @Id
    private String id;

    @Column(name = "last_lat", nullable = false)
    private double lastLat;

    @Column(name = "last_lng", nullable = false)
    private double lastLng;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "total_distance_meters", nullable = false)
    private double totalDistanceMeters;

    /** Null until persisted; lets Spring Data tell new from existing and guards against lost updates. */
    @Version
    private Long version;

    protected Courier() {
    }

    public static Courier firstSeen(String id, GeoPoint location, Instant time) {
        Courier courier = new Courier();
        courier.id = id;
        courier.lastLat = location.lat();
        courier.lastLng = location.lng();
        courier.lastSeenAt = time;
        courier.totalDistanceMeters = 0;
        return courier;
    }

    /** A ping not strictly newer than the last processed one is a duplicate or arrived out of order. */
    public boolean isStale(Instant time) {
        return !time.isAfter(lastSeenAt);
    }

    public void moveTo(GeoPoint location, Instant time, double distanceMeters) {
        if (isStale(time)) {
            throw new IllegalStateException("Cannot move courier " + id + " back in time");
        }
        this.lastLat = location.lat();
        this.lastLng = location.lng();
        this.lastSeenAt = time;
        this.totalDistanceMeters += distanceMeters;
    }

    public String getId() {
        return id;
    }

    public GeoPoint lastLocation() {
        return new GeoPoint(lastLat, lastLng);
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }
}
