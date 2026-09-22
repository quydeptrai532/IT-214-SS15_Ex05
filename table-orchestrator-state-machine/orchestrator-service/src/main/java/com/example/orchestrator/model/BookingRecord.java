package com.example.orchestrator.model;

public class BookingRecord {

    private final String bookingId;
    private final String tableNumber;
    private final long depositAmount;
    private String status;

    public BookingRecord(String bookingId, String tableNumber, long depositAmount, String status) {
        this.bookingId = bookingId;
        this.tableNumber = tableNumber;
        this.depositAmount = depositAmount;
        this.status = status;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public long getDepositAmount() {
        return depositAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
