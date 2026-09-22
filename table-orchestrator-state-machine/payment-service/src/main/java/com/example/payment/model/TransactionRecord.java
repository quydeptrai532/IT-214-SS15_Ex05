package com.example.payment.model;

import java.time.Instant;
import java.util.UUID;

/** Mot dong trong so cai giao dich (audit trail). Bat bien sau khi ghi. */
public record TransactionRecord(String transactionId, String bookingId, long amount,
                                TransactionType type, String status, Instant timestamp) {

    public TransactionRecord(String bookingId, long amount, TransactionType type, String status, Instant timestamp) {
        this(UUID.randomUUID().toString(), bookingId, amount, type, status, timestamp);
    }

    public String signedAmount() {
        return (type == TransactionType.REFUND ? "+" : "-") + amount + " VND";
    }
}
