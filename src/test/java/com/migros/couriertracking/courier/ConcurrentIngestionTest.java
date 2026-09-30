package com.migros.couriertracking.courier;

import com.migros.couriertracking.geo.DistanceCalculator;
import com.migros.couriertracking.geo.GeoPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentIngestionTest {

    private static final int THREADS = 16;

    @Autowired
    private LocationIngestionService ingestionService;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private DistanceCalculator distanceCalculator;

    private final Instant t0 = Instant.now().minus(1, ChronoUnit.HOURS);

    @Test
    void concurrentFirstLocationsCreateCourierOnce() throws Exception {
        String courier = "c-" + UUID.randomUUID();
        List<IngestionResult> results = runConcurrently(i ->
                ingestionService.ingest(new LocationUpdate(courier, t0.plusSeconds(i), new GeoPoint(40, 29))));

        assertThat(courierRepository.findById(courier)).isPresent();
        assertThat(results).hasSize(THREADS);
    }

    /**
     * Points lie on one meridian with latitude increasing with time, so whichever pings win the
     * race, the accepted ones form a monotonic path whose length equals the start-to-end distance.
     * A lost update would break that equality.
     */
    @Test
    void concurrentLocationsOfOneCourierDoNotLoseUpdates() throws Exception {
        String courier = "c-" + UUID.randomUUID();
        GeoPoint start = new GeoPoint(40, 29);
        ingestionService.ingest(new LocationUpdate(courier, t0, start));

        runConcurrently(i -> ingestionService.ingest(
                new LocationUpdate(courier, t0.plusSeconds(i + 1), new GeoPoint(40 + (i + 1) * 0.001, 29))));

        Courier saved = courierRepository.findById(courier).orElseThrow();
        assertThat(saved.getTotalDistanceMeters())
                .isCloseTo(distanceCalculator.distanceMeters(start, saved.lastLocation()), within(0.01))
                .isPositive();
    }

    private List<IngestionResult> runConcurrently(IndexedTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<IngestionResult>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int index = i;
                futures.add(pool.submit((Callable<IngestionResult>) () -> {
                    go.await();
                    return task.run(index);
                }));
            }
            go.countDown();
            List<IngestionResult> results = new ArrayList<>();
            for (Future<IngestionResult> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface IndexedTask {
        IngestionResult run(int index);
    }
}
