package com.migros.couriertracking.api.dto;

public record TotalDistanceResponse(String courierId, double totalDistanceMeters, double totalDistanceKilometers) {

    public static TotalDistanceResponse of(String courierId, double meters) {
        return new TotalDistanceResponse(courierId, meters, meters / 1000.0);
    }
}
