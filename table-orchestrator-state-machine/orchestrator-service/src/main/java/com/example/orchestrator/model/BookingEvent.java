package com.example.orchestrator.model;

public enum BookingEvent {
    RESERVE_TABLE,
    TABLE_RESERVED,
    TABLE_UNAVAILABLE,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    CONFIRM_BOOKING,
    BOOKING_SUCCESS
}
