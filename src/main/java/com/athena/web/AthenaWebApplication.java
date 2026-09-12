package com.athena.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the browser-based UI (epic #71): a Spring Boot
 * application exposing the existing, unmodified domain packages
 * ({@code com.athena.github}, {@code .semantic}, {@code .reviewui},
 * {@code .reviewcontext}, {@code .ai}) over HTTP. This ticket (#72) is
 * the skeleton only — no domain wiring yet.
 */
@SpringBootApplication
public class AthenaWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(AthenaWebApplication.class, args);
    }
}
