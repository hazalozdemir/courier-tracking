package com.migros.couriertracking.courier;

import com.migros.couriertracking.geo.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only log of counted store entrances. */
@Entity
@Table(name = "store_entrance")
public class StoreEntrance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "courier_id", nullable = false, updatable = false)
    private String courierId;

    @Column(name = "store_name", nullable = false, updatable = false)
    private String storeName;

    @Column(name = "entered_at", nullable = false, updatable = false)
    private Instant enteredAt;

    @Column(nullable = false, updatable = false)
    private double lat;

    @Column(nullable = false, updatable = false)
    private double lng;

    protected StoreEntrance() {
    }

    public StoreEntrance(String courierId, String storeName, Instant enteredAt, GeoPoint location) {
        this.courierId = courierId;
        this.storeName = storeName;
        this.enteredAt = enteredAt;
        this.lat = location.lat();
        this.lng = location.lng();
    }

    public String getCourierId() {
        return courierId;
    }

    public String getStoreName() {
        return storeName;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public GeoPoint location() {
        return new GeoPoint(lat, lng);
    }
}
