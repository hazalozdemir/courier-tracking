package com.migros.couriertracking.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer that logs store entrances. Runs only after commit, so a rolled back or retried
 * update never produces a phantom log line. Further observers (notifications, analytics, a
 * message broker publisher) can subscribe to {@link StoreEntranceEvent} without touching ingestion.
 */
@Component
public class StoreEntranceLogger {

    private static final Logger log = LoggerFactory.getLogger(StoreEntranceLogger.class);

    @TransactionalEventListener
    public void on(StoreEntranceEvent event) {
        log.info("Courier {} entered store '{}' at {} ({}, {})",
                event.courierId(), event.storeName(), event.enteredAt(),
                event.location().lat(), event.location().lng());
    }
}
