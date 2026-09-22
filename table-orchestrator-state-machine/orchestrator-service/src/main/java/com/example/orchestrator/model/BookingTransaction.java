package com.example.orchestrator.model;

import com.example.orchestrator.dto.BookingRequest;

public class BookingTransaction {

    private final String bookingId;
    private final String tableNumber;
    private final String customerId;
    private final String customerEmail;
    private final long depositAmount;
    private final BookingScenario scenario;

    private BookingState currentState = BookingState.INITIATED;
    private boolean tableHeld;
    private boolean paymentSettled;

    public BookingTransaction(String bookingId, String tableNumber, String customerId,
                              String customerEmail, long depositAmount, BookingScenario scenario) {
        this.bookingId = bookingId;
        this.tableNumber = tableNumber;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.depositAmount = depositAmount;
        this.scenario = scenario == null ? BookingScenario.SUCCESS : scenario;
    }

    public static BookingTransaction from(BookingRequest request) {
        return new BookingTransaction(request.bookingId(), request.tableNumber(), request.customerId(),
                request.customerEmail(), request.depositAmount(), request.scenario());
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public long getDepositAmount() {
        return depositAmount;
    }

    public BookingScenario getScenario() {
        return scenario;
    }

    public BookingState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(BookingState currentState) {
        this.currentState = currentState;
    }

    public boolean isTableHeld() {
        return tableHeld;
    }

    public void setTableHeld(boolean tableHeld) {
        this.tableHeld = tableHeld;
    }

    public boolean isPaymentSettled() {
        return paymentSettled;
    }

    public void setPaymentSettled(boolean paymentSettled) {
        this.paymentSettled = paymentSettled;
    }
}
