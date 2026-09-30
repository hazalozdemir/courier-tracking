package com.migros.couriertracking.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migros.couriertracking.config.TrackingProperties;
import com.migros.couriertracking.geo.DistanceCalculator;
import com.migros.couriertracking.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable, in-memory catalogue of stores loaded once at startup from {@code stores.json}.
 * The data set is tiny and read on every location update, so a database round trip would be
 * pure overhead. Startup fails fast on a malformed file rather than silently tracking nothing.
 */
@Component
public class StoreRegistry {

    private static final Logger log = LoggerFactory.getLogger(StoreRegistry.class);

    private final List<Store> stores;
    private final DistanceCalculator distanceCalculator;
    private final double radiusMeters;

    public StoreRegistry(TrackingProperties properties, ObjectMapper objectMapper,
                         DistanceCalculator distanceCalculator) {
        this.distanceCalculator = distanceCalculator;
        this.radiusMeters = properties.storeRadiusMeters();
        this.stores = load(properties, objectMapper);
        log.info("Loaded {} stores from {}", stores.size(), properties.storesLocation());
    }

    public List<Store> all() {
        return stores;
    }

    /** Stores whose circumference (configured radius) contains the given point. */
    public List<Store> storesContaining(GeoPoint point) {
        return stores.stream().filter(store -> contains(store, point)).toList();
    }

    public boolean contains(Store store, GeoPoint point) {
        return distanceCalculator.distanceMeters(store.location(), point) <= radiusMeters;
    }

    private static List<Store> load(TrackingProperties properties, ObjectMapper objectMapper) {
        List<StoreJson> raw;
        try (InputStream in = properties.storesLocation().getInputStream()) {
            raw = objectMapper.readValue(in, new TypeReference<>() { });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read stores from " + properties.storesLocation(), e);
        }
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException("No stores defined in " + properties.storesLocation());
        }
        Set<String> names = new HashSet<>();
        return raw.stream().map(json -> {
            if (json.name() == null || json.name().isBlank() || json.lat() == null || json.lng() == null) {
                throw new IllegalStateException("Store entry must have name, lat and lng: " + json);
            }
            if (!names.add(json.name())) {
                throw new IllegalStateException("Duplicate store name: " + json.name());
            }
            return new Store(json.name(), new GeoPoint(json.lat(), json.lng()));
        }).toList();
    }

    private record StoreJson(String name, Double lat, Double lng) {
    }
}
