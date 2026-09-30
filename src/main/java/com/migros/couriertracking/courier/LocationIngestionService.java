package com.migros.couriertracking.courier;

import com.migros.couriertracking.config.TrackingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Entry point for streamed locations. Deliberately not transactional: a retry must run in a
 * fresh transaction, which is only possible from outside the transactional boundary.
 */
@Service
public class LocationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LocationIngestionService.class);

    private final LocationProcessor processor;
    private final Clock clock;
    private final Duration maxClockSkew;

    LocationIngestionService(LocationProcessor processor, Clock clock, TrackingProperties properties) {
        this.processor = processor;
        this.clock = clock;
        this.maxClockSkew = properties.maxClockSkew();
    }

    public IngestionResult ingest(LocationUpdate update) {
        Instant latestAcceptable = clock.instant().plus(maxClockSkew);
        if (update.time().isAfter(latestAcceptable)) {
            // A far-future timestamp would make every later genuine ping look stale and freeze the courier.
            throw new InvalidLocationException("Location time " + update.time() + " is too far in the future");
        }
        try {
            return processor.process(update);
        } catch (DataIntegrityViolationException e) {
            // Two first pings of the same courier raced on insert; the row exists now, so the retry locks it.
            log.debug("Retrying location of courier {} after concurrent creation", update.courierId());
            return processor.process(update);
        }
    }
}
