package com.example.orchestrator.machine;

import com.example.orchestrator.listener.StateChangeListener;
import com.example.orchestrator.model.BookingEvent;
import com.example.orchestrator.model.BookingState;
import com.example.orchestrator.model.BookingTransaction;
import com.example.orchestrator.service.BookingService;
import com.example.orchestrator.service.PaymentOrchestrationService;
import com.example.orchestrator.service.TableOrchestrationService;
import com.example.payment.service.PaymentProcessingService;
import com.example.table.service.TableReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Nha truong dieu phoi luong dat ban - KE THUA tu State Machine cua Bai tap 4 va MO RONG:
 *  - Buoc giu ban dat len truoc tien (Semantic Lock) thay vi thanh toan truoc.
 *  - Them nhanh bu tru TABLE_UNAVAILABLE o buoc xac nhan ban.
 * Van giu nguyen tac: khong tinh tien, khong kiem tra ton kho trong State Machine.
 */
@Service
public class TableBookingStateMachineImpl implements TableBookingStateMachine {

    private static final Logger log = LoggerFactory.getLogger(TableBookingStateMachineImpl.class);

    private final TableOrchestrationService tableService;
    private final PaymentOrchestrationService paymentService;
    private final BookingService bookingService;
    private final StateChangeListener stateChangeListener;

    private final Map<String, BookingState> transactionStates = new ConcurrentHashMap<>();
    private final Map<String, List<String>> transactionLogs = new ConcurrentHashMap<>();

    public TableBookingStateMachineImpl(TableOrchestrationService tableService,
                                        PaymentOrchestrationService paymentService,
                                        BookingService bookingService,
                                        StateChangeListener stateChangeListener) {
        this.tableService = tableService;
        this.paymentService = paymentService;
        this.bookingService = bookingService;
        this.stateChangeListener = stateChangeListener;
    }

    @Override
    public void process(BookingTransaction transaction) {
        String bookingId = transaction.getBookingId();
        transactionStates.putIfAbsent(bookingId, transaction.getCurrentState());
        switch (transaction.getCurrentState()) {
            case INITIATED -> {
                moveTo(bookingId, BookingState.TABLE_RESERVING, BookingEvent.RESERVE_TABLE);
                transaction.setCurrentState(BookingState.TABLE_RESERVING);
                process(transaction);
            }
            case TABLE_RESERVING -> reserveTableWithSemanticLock(transaction);
            case PAYMENT_PENDING -> processPayment(transaction);
            case PAYMENT_COMPLETED -> confirmBooking(transaction);
            case BOOKING_CONFIRMING -> log.debug("[Orchestrator] Booking {} is waiting for Table Service to confirm.", bookingId);
            case BOOKING_CONFIRMED, CANCELLED -> logFinalState(transaction);
        }
    }

    /** Buoc 1: ap dung Semantic Lock de giu ban truoc khi thu tien. */
    private void reserveTableWithSemanticLock(BookingTransaction transaction) {
        String bookingId = transaction.getBookingId();
        String tableNumber = transaction.getTableNumber();
        try {
            tableService.reserveTable(tableNumber, bookingId);
            transaction.setTableHeld(true);
            moveTo(bookingId, BookingState.PAYMENT_PENDING, BookingEvent.TABLE_RESERVED);
            transaction.setCurrentState(BookingState.PAYMENT_PENDING);
            process(transaction);
        } catch (TableReservationService.TableUnavailableException e) {
            log.error("[Orchestrator] Table reservation failed for {} (Already taken).", tableNumber);
            moveTo(bookingId, BookingState.CANCELLED, BookingEvent.TABLE_UNAVAILABLE);
            transaction.setCurrentState(BookingState.CANCELLED);
            compensate(transaction);
            process(transaction);
        }
    }

    /** Buoc 2: tru tien dat coc. */
    private void processPayment(BookingTransaction transaction) {
        String bookingId = transaction.getBookingId();
        try {
            paymentService.processPayment(bookingId, transaction.getDepositAmount());
            transaction.setPaymentSettled(true);
            moveTo(bookingId, BookingState.PAYMENT_COMPLETED, BookingEvent.PAYMENT_SUCCESS);
            transaction.setCurrentState(BookingState.PAYMENT_COMPLETED);
            process(transaction);
        } catch (PaymentProcessingService.PaymentDeclinedException e) {
            log.error("[Orchestrator] Payment failed for booking {} (Declined).", bookingId);
            moveTo(bookingId, BookingState.CANCELLED, BookingEvent.PAYMENT_FAILED);
            transaction.setCurrentState(BookingState.CANCELLED);
            compensate(transaction);
            process(transaction);
        }
    }

    /** Buoc 3: xac nhan ban. That bai thi kich hoat bu tru ngay. */
    private void confirmBooking(BookingTransaction transaction) {
        String bookingId = transaction.getBookingId();
        String tableNumber = transaction.getTableNumber();
        moveTo(bookingId, BookingState.BOOKING_CONFIRMING, BookingEvent.CONFIRM_BOOKING);
        transaction.setCurrentState(BookingState.BOOKING_CONFIRMING);
        try {
            tableService.confirmTable(tableNumber, bookingId);
            moveTo(bookingId, BookingState.BOOKING_CONFIRMED, BookingEvent.BOOKING_SUCCESS);
            transaction.setCurrentState(BookingState.BOOKING_CONFIRMED);
            bookingService.markConfirmed(bookingId);
            process(transaction);
        } catch (TableReservationService.TableUnavailableException e) {
            log.error("[Orchestrator] Table reservation failed for {} (Already taken).", tableNumber);
            moveTo(bookingId, BookingState.CANCELLED, BookingEvent.TABLE_UNAVAILABLE);
            transaction.setCurrentState(BookingState.CANCELLED);
            compensate(transaction);
            process(transaction);
        }
    }

    /** Bu tru theo dung trang thai hien tai: chi hoan tien neu da tru, chi tra ban neu da giu. */
    private void compensate(BookingTransaction transaction) {
        String bookingId = transaction.getBookingId();
        String tableNumber = transaction.getTableNumber();
        if (transaction.isPaymentSettled()) {
            log.info("[Orchestrator] Initiating Compensation: Calling refundPayment for booking {}...", bookingId);
            paymentService.refundPayment(bookingId, transaction.getDepositAmount());
        }
        if (transaction.isTableHeld()) {
            tableService.releaseTable(tableNumber);
        }
        bookingService.markCancelled(bookingId);
    }

    private void logFinalState(BookingTransaction transaction) {
        String tableNumber = transaction.getTableNumber();
        String tableStatus = tableService.statusOf(tableNumber).name();
        if (transaction.getCurrentState() == BookingState.BOOKING_CONFIRMED) {
            log.info("[Final State] Booking {} is CONFIRMED. Table {} is now {}.",
                    transaction.getBookingId(), tableNumber, tableStatus);
            return;
        }
        if (transaction.isTableHeld()) {
            log.info("[Final State] Table {} is now {} (semantic lock released).", tableNumber, tableStatus);
        } else {
            log.info("[Final State] Table {} is {} (this booking never held the lock).", tableNumber, tableStatus);
        }
    }

    private void moveTo(String bookingId, BookingState newState, BookingEvent event) {
        BookingState previousState = transactionStates.put(bookingId, newState);
        stateChangeListener.onStateChanged(bookingId, previousState, newState, event);
        String line = String.format("[Orchestrator] State: %s -> Event: %s -> New State: %s", previousState, event, newState);
        addLog(bookingId, line);
        log.info("{}", line);
    }

    private void addLog(String bookingId, String line) {
        transactionLogs.computeIfAbsent(bookingId, key -> new CopyOnWriteArrayList<>()).add(line);
    }

    @Override
    public BookingState currentState(String bookingId) {
        return transactionStates.get(bookingId);
    }

    @Override
    public List<String> transitionLogs(String bookingId) {
        return List.copyOf(transactionLogs.getOrDefault(bookingId, List.of()));
    }

    @Override
    public void forget(String bookingId) {
        transactionStates.remove(bookingId);
        transactionLogs.remove(bookingId);
    }
}
