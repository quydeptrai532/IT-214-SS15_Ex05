package com.example.orchestrator.listener;

import com.example.orchestrator.model.BookingEvent;
import com.example.orchestrator.model.BookingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ghi vet moi lan chuyen trang thai de phuc vu doi soat.
 * Dat o muc DEBUG de log nghiep vu tren console dung theo dac ta cua de bai.
 */
@Component
public class StateChangeListener {

    private static final Logger log = LoggerFactory.getLogger(StateChangeListener.class);

    public void onStateChanged(String bookingId, BookingState from, BookingState to, BookingEvent event) {
        log.debug("[StateChangeListener] Booking {} : {} -> {} (event={})", bookingId, from, to, event);
    }
}
