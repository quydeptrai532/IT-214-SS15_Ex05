package com.example.payment.service;

import com.example.payment.model.TransactionRecord;
import com.example.payment.model.TransactionType;
import com.example.payment.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Activity implementation cua Payment Service.
 * Trach nhiem: tru tien (PAYMENT) va hoan tien (REFUND) - luon ghi SO CAI, khong bao gio xoa.
 */
@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    public static class PaymentDeclinedException extends RuntimeException {
        public PaymentDeclinedException(String bookingId) {
            super("Payment declined for booking " + bookingId);
        }
    }

    private final TransactionRepository transactionRepository;
    private final AtomicBoolean declineAll = new AtomicBoolean(false);

    public PaymentProcessingService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void processPayment(String bookingId, long amount) {
        if (declineAll.get()) {
            log.error("[PaymentService] Payment declined for booking {} (insufficient funds)", bookingId);
            throw new PaymentDeclinedException(bookingId);
        }
        transactionRepository.save(new TransactionRecord(
                bookingId, amount, TransactionType.PAYMENT, "PROCESSED", Instant.now()));
        log.info("[PaymentService] Payment of {} VND processed for booking {}", amount, bookingId);
    }

    /** Giao dich bu: TAO BAN GHI MOI loai REFUND, tuyet doi khong xoa ban ghi PAYMENT cu. */
    public long refundPayment(String bookingId, long amount) {
        transactionRepository.save(new TransactionRecord(
                bookingId, amount, TransactionType.REFUND, "PROCESSED", Instant.now()));
        log.info("[RefundActivity] Refund of {} VND processed for booking {}.", amount, bookingId);
        log.info("[Transaction] Created REFUND record: +{} VND for booking {} (Audit Trail)", amount, bookingId);
        return amount;
    }

    public void setDeclineAll(boolean value) {
        declineAll.set(value);
    }

    public void reset() {
        declineAll.set(false);
        transactionRepository.clear();
    }
}
