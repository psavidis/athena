package com.athena.analysis.spi;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one {@link ExternalAnalysisProvider} run (ticket #114/
 * #148): either a successful run — {@link #findings()}, possibly empty if
 * the tool found nothing — or a failure, carrying a reason but no
 * findings. A caller running several providers uses this to isolate one
 * provider's failure from the others: one failed {@link ProviderRunResult}
 * never prevents reading another provider's own successful result, or
 * Athena's own analysis, which is the graceful-degradation guarantee this
 * ticket's Functional Requirements call for.
 */
public final class ProviderRunResult {

    private final boolean successful;
    private final List<ExternalFinding> findings;
    private final String failureReason;

    private ProviderRunResult(boolean successful, List<ExternalFinding> findings, String failureReason) {
        this.successful = successful;
        this.findings = List.copyOf(findings);
        this.failureReason = failureReason;
    }

    /** A successful run, with whatever findings the provider reported (possibly none). */
    public static ProviderRunResult success(List<ExternalFinding> findings) {
        Objects.requireNonNull(findings, "findings");
        return new ProviderRunResult(true, findings, null);
    }

    /** A failed run — the provider could not complete (e.g. the tool isn't installed, timed out, threw). */
    public static ProviderRunResult failure(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new ProviderRunResult(false, List.of(), reason);
    }

    public boolean isSuccessful() {
        return successful;
    }

    /** This run's findings. Always empty when {@link #isSuccessful()} is false. */
    public List<ExternalFinding> findings() {
        return findings;
    }

    /** Why this run failed. Only meaningful when {@link #isSuccessful()} is false. */
    public String failureReason() {
        return failureReason;
    }
}
