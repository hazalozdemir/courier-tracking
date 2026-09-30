package com.migros.couriertracking.api.dto;

import com.migros.couriertracking.courier.IngestionResult;

import java.util.List;

public record LocationIngestResponse(IngestionResult.Status status, double distanceAddedMeters, List<String> enteredStores) {

    public static LocationIngestResponse from(IngestionResult result) {
        return new LocationIngestResponse(result.status(), result.distanceAddedMeters(), result.enteredStores());
    }
}
