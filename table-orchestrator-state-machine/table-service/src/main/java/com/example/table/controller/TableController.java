package com.example.table.controller;

import com.example.table.dto.TableInfoResponse;
import com.example.table.model.TableStatus;
import com.example.table.service.TableReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableReservationService tableReservationService;

    public TableController(TableReservationService tableReservationService) {
        this.tableReservationService = tableReservationService;
    }

    /** GET /api/tables/{tableNumber} - nguon du lieu cho UI hien thi trang thai ban. */
    @GetMapping("/{tableNumber}")
    public ResponseEntity<TableInfoResponse> info(@PathVariable String tableNumber) {
        TableStatus status = tableReservationService.statusOf(tableNumber);
        return ResponseEntity.ok(new TableInfoResponse(
                tableNumber,
                status.name(),
                messageOf(status),
                tableReservationService.holderOf(tableNumber),
                tableReservationService.heldSecondsOf(tableNumber)));
    }

    private String messageOf(TableStatus status) {
        return switch (status) {
            case AVAILABLE -> "Bàn trống - có thể đặt ngay";
            case RESERVED -> "Bàn đang được giữ";
            case BOOKED -> "Bàn đã được đặt";
        };
    }
}
