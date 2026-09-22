package com.example.payment.dto;

import com.example.payment.model.TransactionRecord;

import java.util.List;

public record LedgerResponse(String bookingId, long netAmount, List<TransactionRecord> entries) {
}
