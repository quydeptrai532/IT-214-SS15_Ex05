package com.example.table.service;

import com.example.table.model.Table;
import com.example.table.model.TableStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Activity implementation voi SEMANTIC LOCK.
 * Ban di qua 3 trang thai: AVAILABLE -> RESERVED (tam giu, moi nguoi THAY duoc) -> BOOKED.
 * Trang thai RESERVED ton tai de khach khac biet "ban dang duoc giu" thay vi bi bao "het ban"
 * hoac te hon la cung luc dat duoc mot ban (dirty read).
 */
@Service
public class TableReservationService {

    private static final Logger log = LoggerFactory.getLogger(TableReservationService.class);

    public static class TableUnavailableException extends RuntimeException {
        private final String tableNumber;

        public TableUnavailableException(String tableNumber) {
            super("Table " + tableNumber + " is already taken by another booking!");
            this.tableNumber = tableNumber;
        }

        public String getTableNumber() {
            return tableNumber;
        }
    }

    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    private final Set<String> takenByAnotherBooking = ConcurrentHashMap.newKeySet();

    private final Duration configuredHoldTimeout;
    private volatile Duration holdTimeout;

    public TableReservationService(@Value("${saga.table.hold-timeout-ms:300000}") long holdTimeoutMs) {
        this.configuredHoldTimeout = Duration.ofMillis(holdTimeoutMs);
        this.holdTimeout = this.configuredHoldTimeout;
        register("B7");
        register("B8");
    }

    /** Buoc 1: giu ban bang Semantic Lock - chi thanh cong khi ban dang AVAILABLE. */
    public synchronized boolean reserveTable(String tableNumber, String bookingId) {
        Table table = table(tableNumber);
        if (table.getStatus() != TableStatus.AVAILABLE) {
            log.error("[TableService] ERROR: Table {} is already taken by another booking!", tableNumber);
            throw new TableUnavailableException(tableNumber);
        }
        table.setStatus(TableStatus.RESERVED);
        table.setReservedBy(bookingId);
        table.setReservedAt(Instant.now());
        log.info("[TableService] Table {}: AVAILABLE -> RESERVED (Semantic Lock acquired)", tableNumber);
        log.info("[TableService] Table {} reserved for booking {}", tableNumber, bookingId);
        return true;
    }

    /** Buoc 3: chot ban. That bai neu ban da bi khach khac chot truoc. */
    public synchronized void confirmTable(String tableNumber, String bookingId) {
        Table table = table(tableNumber);
        boolean lockIntact = table.getStatus() == TableStatus.RESERVED && bookingId.equals(table.getReservedBy());
        if (!lockIntact || takenByAnotherBooking.contains(tableNumber)) {
            log.error("[TableService] ERROR: Table {} is already taken by another booking!", tableNumber);
            throw new TableUnavailableException(tableNumber);
        }
        table.setStatus(TableStatus.BOOKED);
        log.info("[TableService] Table {}: RESERVED -> BOOKED (booking {} confirmed)", tableNumber, bookingId);
    }

    /** Giao dich bu: tra ban ve AVAILABLE, dong thoi xoa dau vet "bi khach khac lay". */
    public synchronized void releaseTable(String tableNumber) {
        Table table = table(tableNumber);
        takenByAnotherBooking.remove(tableNumber);
        if (table.getStatus() != TableStatus.RESERVED) {
            return;
        }
        table.reset();
        log.info("[TableService] Table {}: RESERVED -> AVAILABLE (Semantic Lock released)", tableNumber);
    }

    /**
     * Mo phong khach khac chot duoc ban truoc trong khi ta dang giu lock (khong ghi log vi day la
     * dieu kien dau vao cua kich ban, khong phai hanh dong cua he thong).
     */
    public synchronized void markTableTakenByAnotherBooking(String tableNumber) {
        takenByAnotherBooking.add(tableNumber);
    }

    /** Tu dong giai phong cac ban RESERVED qua thoi gian giu (mac dinh 5 phut). */
    @Scheduled(fixedDelayString = "${saga.table.auto-release-interval-ms:300000}")
    public void autoReleaseExpiredHolds() {
        Instant deadline = Instant.now().minus(holdTimeout);
        tables.values().stream()
                .filter(table -> table.getStatus() == TableStatus.RESERVED)
                .filter(table -> table.getReservedAt() != null && table.getReservedAt().isBefore(deadline))
                .forEach(table -> {
                    releaseTable(table.getTableNumber());
                    log.info("[TableService] Auto-release expired reservation for table {}", table.getTableNumber());
                });
    }

    public TableStatus statusOf(String tableNumber) {
        return table(tableNumber).getStatus();
    }

    public String holderOf(String tableNumber) {
        return table(tableNumber).getReservedBy();
    }

    public Instant reservedAtOf(String tableNumber) {
        return table(tableNumber).getReservedAt();
    }

    public long heldSecondsOf(String tableNumber) {
        Instant reservedAt = reservedAtOf(tableNumber);
        return reservedAt == null ? 0 : Duration.between(reservedAt, Instant.now()).toSeconds();
    }

    public Duration getHoldTimeout() {
        return holdTimeout;
    }

    public void setHoldTimeout(Duration holdTimeout) {
        this.holdTimeout = holdTimeout;
    }

    public synchronized void reset() {
        takenByAnotherBooking.clear();
        tables.values().forEach(Table::reset);
        holdTimeout = configuredHoldTimeout;
        register("B7");
        register("B8");
    }

    private void register(String tableNumber) {
        tables.computeIfAbsent(tableNumber, Table::new);
    }

    private Table table(String tableNumber) {
        return tables.computeIfAbsent(tableNumber, Table::new);
    }
}
