package com.migros.couriertracking.api.dto;

import com.migros.couriertracking.store.Store;

public record StoreResponse(String name, double lat, double lng) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(store.name(), store.location().lat(), store.location().lng());
    }
}
