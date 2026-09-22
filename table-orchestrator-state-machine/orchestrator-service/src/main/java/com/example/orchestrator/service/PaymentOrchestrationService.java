package com.example.orchestrator.service;

import com.example.payment.service.PaymentProcessingService;
import org.springframework.stereotype.Service;

/**
 * Command Sender: Orchestrator goi sang Payment Service.
 * refundPayment la buoc BU TRU - tao ban ghi REFUND chu khong xoa ban ghi PAYMENT.
 */
@Service
public class PaymentOrchestrationService {

    private final PaymentProcessingService paymentProcessingService;

    public PaymentOrchestrationService(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    public void processPayment(String bookingId, long amount) {
        paymentProcessingService.processPayment(bookingId, amount);
    }

    public long refundPayment(String bookingId, long amount) {
        return paymentProcessingService.refundPayment(bookingId, amount);
    }
}
