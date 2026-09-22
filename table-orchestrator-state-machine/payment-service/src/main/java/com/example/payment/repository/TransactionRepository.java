package com.example.payment.repository;

import com.example.payment.model.TransactionRecord;
import com.example.payment.model.TransactionType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * So cai giao dich CHI GHI THEM (append-only) - dung cho kiem toan.
 * Co y KHONG cung cap update/delete: sua hay xoa mot but toan da ghi la pha vo tinh toan ven.
 */
@Repository
public class TransactionRepository {

    private final List<TransactionRecord> ledger = new CopyOnWriteArrayList<>();

    public TransactionRecord save(TransactionRecord record) {
        ledger.add(record);
        return record;
    }

    public List<TransactionRecord> findByBookingId(String bookingId) {
        return ledger.stream().filter(record -> record.bookingId().equals(bookingId)).toList();
    }

    public List<TransactionRecord> findByType(TransactionType type) {
        return ledger.stream().filter(record -> record.type() == type).toList();
    }

    public List<TransactionRecord> findAll() {
        return List.copyOf(ledger);
    }

    public long netAmountOf(String bookingId) {
        return findByBookingId(bookingId).stream()
                .mapToLong(record -> record.type() == TransactionType.REFUND ? record.amount() : -record.amount())
                .sum();
    }

    public void clear() {
        ledger.clear();
    }
}
