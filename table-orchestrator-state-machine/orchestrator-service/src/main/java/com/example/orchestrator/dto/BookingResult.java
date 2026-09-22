package com.example.orchestrator.dto;

import com.example.payment.model.TransactionRecord;

import java.util.List;

public record BookingResult(String bookingId, String finalState, String tableStatus, String bookingStatus,
                            List<String> logs, List<TransactionRecord> auditTrail) {
}
