package com.example.table;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TableApplication {

    public static void main(String[] args) {
        SpringApplication.run(TableApplication.class, args);
    }
}
