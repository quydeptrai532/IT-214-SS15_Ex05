package com.example.orchestrator.model;

public enum BookingState {
    INITIATED,
    TABLE_RESERVING,
    PAYMENT_PENDING,
    PAYMENT_COMPLETED,
    BOOKING_CONFIRMING,
    BOOKING_CONFIRMED,
    CANCELLED
}
