package com.migros.couriertracking.courier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourierQueryService {

    private final CourierRepository courierRepository;
    private final StoreEntranceRepository entranceRepository;

    CourierQueryService(CourierRepository courierRepository, StoreEntranceRepository entranceRepository) {
        this.courierRepository = courierRepository;
        this.entranceRepository = entranceRepository;
    }

    /** @return total travelled distance in meters */
    public Double getTotalTravelDistance(String courierId) {
        return courierRepository.findById(courierId)
                .map(Courier::getTotalDistanceMeters)
                .orElseThrow(() -> new CourierNotFoundException(courierId));
    }

    public List<StoreEntrance> getStoreEntrances(String courierId) {
        if (!courierRepository.existsById(courierId)) {
            throw new CourierNotFoundException(courierId);
        }
        return entranceRepository.findByCourierIdOrderByEnteredAtAsc(courierId);
    }
}
