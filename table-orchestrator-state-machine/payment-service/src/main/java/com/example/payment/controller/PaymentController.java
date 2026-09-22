package com.example.payment.controller;

import com.example.payment.dto.LedgerResponse;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.repository.TransactionRepository;
import com.example.payment.service.PaymentProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentProcessingService paymentProcessingService;
    private final TransactionRepository transactionRepository;

    public PaymentController(PaymentProcessingService paymentProcessingService,
                             TransactionRepository transactionRepository) {
        this.paymentProcessingService = paymentProcessingService;
        this.transactionRepository = transactionRepository;
    }

    /** POST /api/payments/charge - tru tien dat coc. */
    @PostMapping("/charge")
    public ResponseEntity<String> charge(@RequestBody PaymentRequest request) {
        try {
            paymentProcessingService.processPayment(request.bookingId(), request.amount());
            return ResponseEntity.ok("PAYMENT_PROCESSED");
        } catch (PaymentProcessingService.PaymentDeclinedException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body("PAYMENT_DECLINED");
        }
    }

    /** POST /api/payments/refund - tao ban ghi bu tru (khong xoa ban ghi cu). */
    @PostMapping("/refund")
    public ResponseEntity<String> refund(@RequestBody PaymentRequest request) {
        long refunded = paymentProcessingService.refundPayment(request.bookingId(), request.amount());
        return ResponseEntity.ok("REFUNDED:" + refunded);
    }

    /** GET /api/payments/ledger/{bookingId} - tra so cai de doi soat/kiem toan. */
    @GetMapping("/ledger/{bookingId}")
    public ResponseEntity<LedgerResponse> ledger(@PathVariable String bookingId) {
        return ResponseEntity.ok(new LedgerResponse(
                bookingId,
                transactionRepository.netAmountOf(bookingId),
                transactionRepository.findByBookingId(bookingId)));
    }
}
