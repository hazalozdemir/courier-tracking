package com.migros.couriertracking.api.dto;

import com.migros.couriertracking.courier.StoreEntrance;

import java.time.Instant;

public record StoreEntranceResponse(String courierId, String storeName, Instant enteredAt, double lat, double lng) {

    public static StoreEntranceResponse from(StoreEntrance entrance) {
        return new StoreEntranceResponse(entrance.getCourierId(), entrance.getStoreName(), entrance.getEnteredAt(),
                entrance.location().lat(), entrance.location().lng());
    }
}
