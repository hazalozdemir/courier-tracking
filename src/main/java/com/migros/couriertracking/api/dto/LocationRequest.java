package com.migros.couriertracking.api.dto;

import com.migros.couriertracking.courier.LocationUpdate;
import com.migros.couriertracking.geo.GeoPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record LocationRequest(
        @Schema(example = "courier-1")
        @NotBlank @Pattern(regexp = CourierIds.PATTERN, message = CourierIds.MESSAGE) String courier,
        @Schema(example = "2026-09-27T10:15:30Z", description = "ISO-8601 instant")
        @NotNull Instant time,
        @Schema(example = "40.9923307")
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @Schema(example = "29.1244229")
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {

    public LocationUpdate toLocationUpdate() {
        return new LocationUpdate(courier, time, new GeoPoint(lat, lng));
    }
}
