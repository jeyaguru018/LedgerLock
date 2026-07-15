package com.ledgerlock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class LedgerlockApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerlockApplication.class, args);
    }
}
