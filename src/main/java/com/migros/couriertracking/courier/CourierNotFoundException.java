package com.migros.couriertracking.courier;

public class CourierNotFoundException extends RuntimeException {

    public CourierNotFoundException(String courierId) {
        super("Courier not found: " + courierId);
    }
}
