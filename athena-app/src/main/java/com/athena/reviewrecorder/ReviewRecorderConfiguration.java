package com.athena.reviewrecorder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes the system {@link Clock} as a Spring-managed bean (ticket #203)
 * so {@link ReviewRecordingRegistry} depends on the abstraction rather
 * than {@code Clock.systemUTC()} directly — the seam a test substitutes a
 * fixed/mutable clock through, per CODE_STYLE.md's test-design guidance
 * on non-deterministic boundaries.
 */
@Configuration
public class ReviewRecorderConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
