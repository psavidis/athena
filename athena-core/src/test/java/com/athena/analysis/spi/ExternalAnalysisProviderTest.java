package com.athena.analysis.spi;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@link ExternalAnalysisProvider} contract itself via a
 * minimal in-memory fake — proving the interface is actually implementable
 * and usable the way a real provider (SonarJava, ESLint, future tickets)
 * would be, before any real provider exists.
 */
class ExternalAnalysisProviderTest {

    @Test
    void aProviderReportsItsOwnIdentityAndLanguage() {
        ExternalAnalysisProvider provider = new FakeProvider("sonarjava", "java", ProviderRunResult.success(List.of()));

        assertThat(provider.providerId()).isEqualTo("sonarjava");
        assertThat(provider.language()).isEqualTo("java");
    }

    @Test
    void aSuccessfulProviderRunReturnsItsFindings() {
        ExternalFinding finding = ExternalFinding
                .builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "message", SourceLocation.ofFile("Order.java"))
                .build();
        ExternalAnalysisProvider provider = new FakeProvider("sonarjava", "java", ProviderRunResult.success(List.of(finding)));

        ProviderRunResult result = provider.analyze(Path.of("."), List.of(), ProviderConfiguration.enabled(Map.of()));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.findings()).containsExactly(finding);
    }

    @Test
    void aFailedProviderRunNeverThrows() {
        ExternalAnalysisProvider provider =
                new FakeProvider("sonarjava", "java", ProviderRunResult.failure("tool not installed"));

        ProviderRunResult result = provider.analyze(Path.of("."), List.of(), ProviderConfiguration.enabled(Map.of()));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).isEqualTo("tool not installed");
    }

    @Test
    void differentProvidersCanTargetDifferentLanguagesIndependently() {
        ExternalAnalysisProvider sonarJava = new FakeProvider("sonarjava", "java", ProviderRunResult.success(List.of()));
        ExternalAnalysisProvider eslint = new FakeProvider("eslint", "javascript", ProviderRunResult.success(List.of()));

        assertThat(sonarJava.language()).isEqualTo("java");
        assertThat(eslint.language()).isEqualTo("javascript");
    }

    /** A minimal fake standing in for a real external analyzer (ticket #114/#148's Validation requirement). */
    private static final class FakeProvider implements ExternalAnalysisProvider {
        private final String providerId;
        private final String language;
        private final ProviderRunResult result;

        FakeProvider(String providerId, String language, ProviderRunResult result) {
            this.providerId = providerId;
            this.language = language;
            this.result = result;
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
            return result;
        }
    }
}
