package com.migros.couriertracking.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Business tunables. Kept in configuration because every one of them is a product decision
 * (radius, cooldown) or an operational safety limit (clock skew), not a code constant.
 */
@Validated
@ConfigurationProperties(prefix = "courier-tracking")
public record TrackingProperties(
        @NotNull Resource storesLocation,
        @Positive double storeRadiusMeters,
        @NotNull Duration reentryCooldown,
        @NotNull Duration maxClockSkew) {
}
