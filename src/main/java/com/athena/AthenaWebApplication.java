package com.athena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the browser-based UI (epic #71): a Spring Boot
 * application exposing the existing, unmodified domain packages
 * ({@code com.athena.github}, {@code .semantic}, {@code .reviewui},
 * {@code .reviewcontext}, {@code .ai}) over HTTP. Lives at the root
 * package (not {@code com.athena.web}) so Spring's component scan —
 * which starts from this class's own package downward — reaches every
 * {@code com.athena.*} sub-package, not just the web layer's own.
 */
@SpringBootApplication
public class AthenaWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(AthenaWebApplication.class, args);
    }
}
