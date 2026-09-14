package com.athena.analysis.spi;

/**
 * A provider-independent severity for an {@link ExternalFinding} (ticket
 * #114/#148) — every provider's own severity vocabulary (SonarJava's
 * BLOCKER/CRITICAL/MAJOR/MINOR/INFO, ESLint's error/warning, etc.) maps
 * into this one scale at the provider adapter boundary, so Athena's core
 * never needs to know a provider's own terminology.
 */
public enum ExternalFindingSeverity {
    BLOCKER,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}
