package com.migros.couriertracking.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class HaversineDistanceCalculatorTest {

    private final DistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void samePointIsZero() {
        GeoPoint p = new GeoPoint(40.9923307, 29.1244229);
        assertThat(calculator.distanceMeters(p, p)).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        double meters = calculator.distanceMeters(new GeoPoint(40, 29), new GeoPoint(41, 29));
        assertThat(meters).isCloseTo(111_195, within(1.0));
    }

    @Test
    void isSymmetric() {
        GeoPoint atasehir = new GeoPoint(40.9923307, 29.1244229);
        GeoPoint beylikduzu = new GeoPoint(41.0066851, 28.6552262);
        assertThat(calculator.distanceMeters(atasehir, beylikduzu))
                .isCloseTo(calculator.distanceMeters(beylikduzu, atasehir), within(1e-6))
                .isCloseTo(39_400, within(200.0));
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThatThrownBy(() -> new GeoPoint(91, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, -181)).isInstanceOf(IllegalArgumentException.class);
    }
}
