package com.migros.couriertracking.courier;

import java.util.List;

public record IngestionResult(Status status, double distanceAddedMeters, List<String> enteredStores) {

    public enum Status {
        /** The location was applied to the courier's state. */
        PROCESSED,
        /** The location was not newer than the last processed one (duplicate or out of order). */
        IGNORED_STALE
    }

    public static IngestionResult processed(double distanceAddedMeters, List<String> enteredStores) {
        return new IngestionResult(Status.PROCESSED, distanceAddedMeters, List.copyOf(enteredStores));
    }

    public static IngestionResult ignoredStale() {
        return new IngestionResult(Status.IGNORED_STALE, 0, List.of());
    }
}
