package com.athena.plugins;

import com.athena.analysis.spi.ExternalAnalysisProvider;
import com.athena.analysis.spi.ExternalFinding;
import com.athena.analysis.spi.ExternalFindingSeverity;
import com.athena.analysis.spi.ProviderConfiguration;
import com.athena.analysis.spi.ProviderRunResult;
import com.athena.analysis.spi.SourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal fixture standing in for a real external analyzer (ticket
 * #114/#149's Validation requirement) — not a real tool integration
 * (SonarJava is #116, ESLint is #117), just enough to prove
 * {@link com.athena.semantic.PrAnalyzer} actually runs a configured
 * provider and folds its findings into the {@link
 * com.athena.semantic.AnalysisResult}, in isolation from other providers.
 */
public class FakeExternalAnalysisProvider implements ExternalAnalysisProvider {

    private final String providerId;
    private final String language;
    private final List<String> filesToReportOn = new ArrayList<>();
    private String failureReason;

    public FakeExternalAnalysisProvider(String providerId, String language) {
        this.providerId = providerId;
        this.language = language;
    }

    /** Configures this provider to report exactly one finding on {@code filePath} when run. */
    public FakeExternalAnalysisProvider reportingOn(String filePath) {
        filesToReportOn.add(filePath);
        return this;
    }

    /** Configures this provider to fail with {@code reason} instead of running normally. */
    public FakeExternalAnalysisProvider failingWith(String reason) {
        this.failureReason = reason;
        return this;
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public String language() {
        return language;
    }

    @Override
    public ProviderRunResult analyze(Path sourceRoot, List<Path> changedFiles, ProviderConfiguration configuration) {
        if (failureReason != null) {
            return ProviderRunResult.failure(failureReason);
        }
        List<ExternalFinding> findings = filesToReportOn.stream()
                .map(filePath -> ExternalFinding
                        .builder(providerId, language, "fake-rule", ExternalFindingSeverity.MEDIUM,
                                "Fake finding from " + providerId, SourceLocation.ofFile(filePath))
                        .build())
                .toList();
        return ProviderRunResult.success(findings);
    }
}
