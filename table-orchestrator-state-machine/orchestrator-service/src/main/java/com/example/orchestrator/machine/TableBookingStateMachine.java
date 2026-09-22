package com.example.orchestrator.machine;

import com.example.orchestrator.model.BookingState;
import com.example.orchestrator.model.BookingTransaction;

import java.util.List;

public interface TableBookingStateMachine {

    void process(BookingTransaction transaction);

    BookingState currentState(String bookingId);

    List<String> transitionLogs(String bookingId);

    void forget(String bookingId);
}
