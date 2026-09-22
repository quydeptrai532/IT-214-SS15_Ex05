package com.example.table.model;

import java.time.Instant;

public class Table {

    private final String tableNumber;
    private TableStatus status = TableStatus.AVAILABLE;
    private String reservedBy;
    private Instant reservedAt;

    public Table(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    public String getReservedBy() {
        return reservedBy;
    }

    public void setReservedBy(String reservedBy) {
        this.reservedBy = reservedBy;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(Instant reservedAt) {
        this.reservedAt = reservedAt;
    }

    public void reset() {
        this.status = TableStatus.AVAILABLE;
        this.reservedBy = null;
        this.reservedAt = null;
    }
}
