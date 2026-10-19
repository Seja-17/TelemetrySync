package com.fordprep.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TelemetrySyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetrySyncApplication.class, args);
    }
}