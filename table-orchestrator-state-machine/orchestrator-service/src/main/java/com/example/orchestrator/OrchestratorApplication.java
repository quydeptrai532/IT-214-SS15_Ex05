package com.example.orchestrator;

import com.example.payment.repository.TransactionRepository;
import com.example.payment.service.PaymentProcessingService;
import com.example.table.service.TableReservationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({TableReservationService.class, PaymentProcessingService.class, TransactionRepository.class})
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
