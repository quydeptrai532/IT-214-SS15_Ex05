package com.example.orchestrator.controller;

import com.example.orchestrator.dto.BookingRequest;
import com.example.orchestrator.dto.BookingResult;
import com.example.orchestrator.service.TableBookingSagaOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final TableBookingSagaOrchestrator orchestrator;

    public BookingController(TableBookingSagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** POST /api/bookings - kich hoat State Machine cho mot yeu cau dat ban. */
    @PostMapping
    public ResponseEntity<BookingResult> book(@RequestBody BookingRequest request) {
        return ResponseEntity.ok(orchestrator.execute(request));
    }
}
