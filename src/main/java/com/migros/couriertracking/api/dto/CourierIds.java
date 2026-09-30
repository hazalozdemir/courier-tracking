package com.migros.couriertracking.api.dto;

/** Courier ids are opaque but bounded, which keeps them safe to log, index and put in URLs. */
public final class CourierIds {

    public static final String PATTERN = "^[A-Za-z0-9_-]{1,64}$";
    public static final String MESSAGE = "must be 1-64 characters of letters, digits, '_' or '-'";

    private CourierIds() {
    }
}
