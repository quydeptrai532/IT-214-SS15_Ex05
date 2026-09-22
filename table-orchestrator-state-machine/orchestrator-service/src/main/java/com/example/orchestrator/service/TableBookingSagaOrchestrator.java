package com.example.orchestrator.service;

import com.example.orchestrator.dto.BookingRequest;
import com.example.orchestrator.dto.BookingResult;
import com.example.orchestrator.machine.TableBookingStateMachine;
import com.example.orchestrator.model.BookingScenario;
import com.example.orchestrator.model.BookingTransaction;
import com.example.payment.repository.TransactionRepository;
import org.springframework.stereotype.Service;

/**
 * Diem vao cua Saga: dung giao dich tu yeu cau dat ban roi giao cho State Machine chay.
 * Truong `scenario` chi dung de dung san dieu kien dau vao khi chay demo.
 */
@Service
public class TableBookingSagaOrchestrator {

    private final TableBookingStateMachine stateMachine;
    private final TableOrchestrationService tableService;
    private final BookingService bookingService;
    private final TransactionRepository transactionRepository;

    public TableBookingSagaOrchestrator(TableBookingStateMachine stateMachine,
                                        TableOrchestrationService tableService,
                                        BookingService bookingService,
                                        TransactionRepository transactionRepository) {
        this.stateMachine = stateMachine;
        this.tableService = tableService;
        this.bookingService = bookingService;
        this.transactionRepository = transactionRepository;
    }

    public BookingResult execute(BookingRequest request) {
        BookingTransaction transaction = BookingTransaction.from(request);
        bookingService.register(transaction.getBookingId(), transaction.getTableNumber(), transaction.getDepositAmount());
        if (transaction.getScenario() == BookingScenario.FAILED_ALREADY_TAKEN) {
            tableService.markTableTakenByAnotherBooking(transaction.getTableNumber());
        }
        stateMachine.forget(transaction.getBookingId());
        stateMachine.process(transaction);
        return new BookingResult(
                transaction.getBookingId(),
                stateMachine.currentState(transaction.getBookingId()).name(),
                tableService.statusOf(transaction.getTableNumber()).name(),
                bookingService.statusOf(transaction.getBookingId()),
                stateMachine.transitionLogs(transaction.getBookingId()),
                transactionRepository.findByBookingId(transaction.getBookingId()));
    }
}
