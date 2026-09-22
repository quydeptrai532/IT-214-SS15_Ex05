package com.example.orchestrator.service;

import com.example.orchestrator.model.BookingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Luu vet don dat ban va trang thai cuoi cung cua don. */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final Map<String, BookingRecord> bookings = new ConcurrentHashMap<>();

    public void register(String bookingId, String tableNumber, long depositAmount) {
        bookings.put(bookingId, new BookingRecord(bookingId, tableNumber, depositAmount, "INITIATED"));
    }

    public void markCancelled(String bookingId) {
        BookingRecord record = bookings.get(bookingId);
        if (record == null) {
            return;
        }
        record.setStatus("CANCELLED");
        log.info("[BookingService] Booking {} updated to CANCELLED.", bookingId);
    }

    public void markConfirmed(String bookingId) {
        BookingRecord record = bookings.get(bookingId);
        if (record == null) {
            return;
        }
        record.setStatus("CONFIRMED");
        log.info("[BookingService] Booking {} updated to CONFIRMED.", bookingId);
    }

    public String statusOf(String bookingId) {
        BookingRecord record = bookings.get(bookingId);
        return record == null ? null : record.getStatus();
    }

    public void clear() {
        bookings.clear();
    }
}
