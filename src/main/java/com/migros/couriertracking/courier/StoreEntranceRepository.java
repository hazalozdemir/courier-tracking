package com.migros.couriertracking.courier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreEntranceRepository extends JpaRepository<StoreEntrance, Long> {

    Optional<StoreEntrance> findTopByCourierIdAndStoreNameOrderByEnteredAtDesc(String courierId, String storeName);

    List<StoreEntrance> findByCourierIdOrderByEnteredAtAsc(String courierId);
}
