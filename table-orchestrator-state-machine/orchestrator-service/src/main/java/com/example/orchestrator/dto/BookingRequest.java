package com.example.orchestrator.dto;

import com.example.orchestrator.model.BookingScenario;

public record BookingRequest(String bookingId, String tableNumber, String customerId,
                             String customerEmail, long depositAmount, BookingScenario scenario) {
}
