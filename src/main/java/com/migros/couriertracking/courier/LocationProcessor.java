package com.migros.couriertracking.courier;

import com.migros.couriertracking.config.TrackingProperties;
import com.migros.couriertracking.event.StoreEntranceEvent;
import com.migros.couriertracking.geo.DistanceCalculator;
import com.migros.couriertracking.geo.GeoPoint;
import com.migros.couriertracking.store.Store;
import com.migros.couriertracking.store.StoreRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Applies one location update atomically: distance accumulation and entrance detection commit
 * or roll back together, under a row lock on the courier.
 */
@Component
class LocationProcessor {

    private final CourierRepository courierRepository;
    private final StoreEntranceRepository entranceRepository;
    private final StoreRegistry storeRegistry;
    private final DistanceCalculator distanceCalculator;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration reentryCooldown;

    LocationProcessor(CourierRepository courierRepository,
                      StoreEntranceRepository entranceRepository,
                      StoreRegistry storeRegistry,
                      DistanceCalculator distanceCalculator,
                      ApplicationEventPublisher eventPublisher,
                      TrackingProperties properties) {
        this.courierRepository = courierRepository;
        this.entranceRepository = entranceRepository;
        this.storeRegistry = storeRegistry;
        this.distanceCalculator = distanceCalculator;
        this.eventPublisher = eventPublisher;
        this.reentryCooldown = properties.reentryCooldown();
    }

    @Transactional
    public IngestionResult process(LocationUpdate update) {
        Optional<Courier> existing = courierRepository.findByIdForUpdate(update.courierId());

        GeoPoint previousLocation = null;
        double distance = 0;
        if (existing.isEmpty()) {
            // Flush immediately so a concurrent first ping for the same courier fails here with a
            // DataIntegrityViolationException the caller can retry, instead of at commit time.
            courierRepository.saveAndFlush(Courier.firstSeen(update.courierId(), update.location(), update.time()));
        } else {
            Courier courier = existing.get();
            if (courier.isStale(update.time())) {
                return IngestionResult.ignoredStale();
            }
            previousLocation = courier.lastLocation();
            distance = distanceCalculator.distanceMeters(previousLocation, update.location());
            courier.moveTo(update.location(), update.time(), distance);
        }

        return IngestionResult.processed(distance, recordEntrances(update, previousLocation));
    }

    /**
     * An entrance is an outside-to-inside transition of the store circumference. It is not
     * counted if the same courier's previous counted entrance to that store is within the cooldown.
     */
    private List<String> recordEntrances(LocationUpdate update, GeoPoint previousLocation) {
        List<String> entered = new ArrayList<>();
        for (Store store : storeRegistry.storesContaining(update.location())) {
            boolean wasAlreadyInside = previousLocation != null && storeRegistry.contains(store, previousLocation);
            if (wasAlreadyInside || isWithinCooldown(update.courierId(), store, update.time())) {
                continue;
            }
            entranceRepository.save(new StoreEntrance(update.courierId(), store.name(), update.time(), update.location()));
            eventPublisher.publishEvent(new StoreEntranceEvent(update.courierId(), store.name(), update.time(), update.location()));
            entered.add(store.name());
        }
        return entered;
    }

    private boolean isWithinCooldown(String courierId, Store store, Instant time) {
        return entranceRepository.findTopByCourierIdAndStoreNameOrderByEnteredAtDesc(courierId, store.name())
                .map(last -> Duration.between(last.getEnteredAt(), time).compareTo(reentryCooldown) < 0)
                .orElse(false);
    }
}
