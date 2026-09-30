package com.migros.couriertracking.api;

import com.migros.couriertracking.courier.StoreEntranceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourierTrackingApiTest {

    private static final String ATASEHIR = "Ataşehir MMM Migros";
    // ~19 m north of Ataşehir MMM Migros: inside the 100 m radius
    private static final double IN_LAT = 40.9925, IN_LNG = 29.1244229;
    // ~408 m north of Ataşehir MMM Migros: outside every store radius
    private static final double OUT_LAT = 40.9960, OUT_LNG = 29.1244229;

    private final Instant t0 = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StoreEntranceRepository entranceRepository;

    @Test
    void accumulatesTotalTravelDistance() throws Exception {
        String courier = newCourier();
        send(courier, t0, 40.0, 29.0).andExpect(jsonPath("$.distanceAddedMeters").value(0.0));
        send(courier, t0.plusSeconds(60), 40.01, 29.0);
        send(courier, t0.plusSeconds(120), 40.02, 29.0);

        mvc.perform(get("/api/v1/couriers/{id}/total-distance", courier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDistanceMeters").value(closeTo(2_223.9, 1.0)))
                .andExpect(jsonPath("$.totalDistanceKilometers").value(closeTo(2.2239, 0.001)));
    }

    @Test
    void ignoresDuplicateAndOutOfOrderLocations() throws Exception {
        String courier = newCourier();
        send(courier, t0, 40.0, 29.0);
        send(courier, t0.plusSeconds(60), 40.01, 29.0);

        send(courier, t0.plusSeconds(60), 40.01, 29.0).andExpect(jsonPath("$.status").value("IGNORED_STALE"));
        send(courier, t0.plusSeconds(30), 41.0, 29.0).andExpect(jsonPath("$.status").value("IGNORED_STALE"));

        mvc.perform(get("/api/v1/couriers/{id}/total-distance", courier))
                .andExpect(jsonPath("$.totalDistanceMeters").value(closeTo(1_112.0, 1.0)));
    }

    @Test
    void countsEntranceOnlyOnTransitionAndOutsideCooldown() throws Exception {
        String courier = newCourier();
        send(courier, t0, OUT_LAT, OUT_LNG).andExpect(jsonPath("$.enteredStores", hasSize(0)));
        send(courier, t0.plusSeconds(10), IN_LAT, IN_LNG).andExpect(jsonPath("$.enteredStores", contains(ATASEHIR)));
        // still inside: same visit
        send(courier, t0.plusSeconds(20), IN_LAT, IN_LNG).andExpect(jsonPath("$.enteredStores", hasSize(0)));
        // leaves and re-enters 40 s after the counted entrance: within cooldown
        send(courier, t0.plusSeconds(30), OUT_LAT, OUT_LNG);
        send(courier, t0.plusSeconds(50), IN_LAT, IN_LNG).andExpect(jsonPath("$.enteredStores", hasSize(0)));
        // leaves and re-enters exactly 1 minute after the counted entrance: a new entrance
        send(courier, t0.plusSeconds(60), OUT_LAT, OUT_LNG);
        send(courier, t0.plusSeconds(70), IN_LAT, IN_LNG).andExpect(jsonPath("$.enteredStores", contains(ATASEHIR)));

        mvc.perform(get("/api/v1/couriers/{id}/store-entrances", courier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].storeName").value(ATASEHIR))
                .andExpect(jsonPath("$[0].enteredAt").value(t0.plusSeconds(10).toString()))
                .andExpect(jsonPath("$[1].enteredAt").value(t0.plusSeconds(70).toString()));
    }

    @Test
    void firstLocationInsideStoreCountsAsEntrance() throws Exception {
        String courier = newCourier();
        send(courier, t0, IN_LAT, IN_LNG).andExpect(jsonPath("$.enteredStores", contains(ATASEHIR)));
        assertThat(entranceRepository.findByCourierIdOrderByEnteredAtAsc(courier)).hasSize(1);
    }

    @Test
    void unknownCourierIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/couriers/{id}/total-distance", newCourier()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        send(newCourier(), t0, 91, 29).andExpect(status().isBadRequest());
        send("bad id!", t0, 40, 29).andExpect(status().isBadRequest());
        send(newCourier(), Instant.now().plus(1, ChronoUnit.DAYS), 40, 29).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content("{\"courier\":\"c\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/couriers/{id}/total-distance", "a".repeat(65)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsStores() throws Exception {
        mvc.perform(get("/api/v1/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    private ResultActions send(String courier, Instant time, double lat, double lng) throws Exception {
        String body = """
                {"courier":"%s","time":"%s","lat":%s,"lng":%s}""".formatted(courier, time, lat, lng);
        return mvc.perform(post("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String newCourier() {
        return "c-" + UUID.randomUUID();
    }
}
