package com.example.orchestrator.service;

import com.example.table.model.TableStatus;
import com.example.table.service.TableReservationService;
import org.springframework.stereotype.Service;

/** Command Sender: Orchestrator goi sang Table Service. Khong chua nghiep vu giu ban. */
@Service
public class TableOrchestrationService {

    private final TableReservationService tableReservationService;

    public TableOrchestrationService(TableReservationService tableReservationService) {
        this.tableReservationService = tableReservationService;
    }

    public void reserveTable(String tableNumber, String bookingId) {
        tableReservationService.reserveTable(tableNumber, bookingId);
    }

    public void confirmTable(String tableNumber, String bookingId) {
        tableReservationService.confirmTable(tableNumber, bookingId);
    }

    public void releaseTable(String tableNumber) {
        tableReservationService.releaseTable(tableNumber);
    }

    public void markTableTakenByAnotherBooking(String tableNumber) {
        tableReservationService.markTableTakenByAnotherBooking(tableNumber);
    }

    public TableStatus statusOf(String tableNumber) {
        return tableReservationService.statusOf(tableNumber);
    }
}
