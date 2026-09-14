package com.athena.plugins;

import com.athena.analysis.spi.ExternalAnalysisProvider;
import com.athena.analysis.spi.ProviderConfiguration;
import com.athena.analysis.spi.ProviderRunResult;
import com.athena.semantic.AnalysisResult;
import com.athena.semantic.PrAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link PrAnalyzer}'s provider-running logic
 * (ticket #114/#149) — {@link ExternalFindingsAsEvidenceSteps} covers the
 * black-box Gherkin path using a well-behaved {@link
 * FakeExternalAnalysisProvider}; this focuses on edge cases that scenario
 * doesn't reach: a provider that throws instead of returning a failed
 * result, and a disabled provider configuration.
 */
class PrAnalyzerExternalProviderTest {

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-provider-base");
        headRoot = Files.createTempDirectory("athena-provider-head");
    }

    @AfterEach
    void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Test
    void aProviderThatThrowsIsIsolatedRatherThanFailingTheWholeAnalysis() {
        ExternalAnalysisProvider throwingProvider = new ExternalAnalysisProvider() {
            @Override
            public String providerId() {
                return "broken-provider";
            }

            @Override
            public String language() {
                return "java";
            }

            @Override
            public ProviderRunResult analyze(Path sourceRoot, List<Path> changedFiles, ProviderConfiguration configuration) {
                throw new IllegalStateException("tool crashed");
            }
        };

        AnalysisResult result = newPrAnalyzer(Map.of(throwingProvider, ProviderConfiguration.enabled(Map.of())))
                .analyze(baseRoot, headRoot);

        assertThat(result.externalFindings()).isEmpty();
        assertThat(result.status()).isNotNull();
    }

    @Test
    void aDisabledProviderIsNeverRun() {
        FakeExternalAnalysisProvider provider = new FakeExternalAnalysisProvider("sonarjava", "java").reportingOn("Order.java");

        AnalysisResult result = newPrAnalyzer(Map.of(provider, ProviderConfiguration.disabled()))
                .analyze(baseRoot, headRoot);

        assertThat(result.externalFindings()).isEmpty();
    }

    @Test
    void theDefaultConstructorRunsNoExternalProviders() {
        PrAnalyzer prAnalyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

        AnalysisResult result = prAnalyzer.analyze(baseRoot, headRoot);

        assertThat(result.externalFindings()).isEmpty();
    }

    private PrAnalyzer newPrAnalyzer(Map<ExternalAnalysisProvider, ProviderConfiguration> providers) {
        return new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins(), providers);
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
