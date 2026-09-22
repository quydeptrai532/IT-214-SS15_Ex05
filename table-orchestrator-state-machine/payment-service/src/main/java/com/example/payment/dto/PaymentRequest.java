package com.example.payment.dto;

public record PaymentRequest(String bookingId, long amount) {
}
