package com.indeci;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IndeciBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndeciBackendApplication.class, args);
    }
}