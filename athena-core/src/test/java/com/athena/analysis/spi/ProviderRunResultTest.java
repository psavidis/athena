package com.athena.analysis.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRunResultTest {

    private static final ExternalFinding FINDING = ExternalFinding
            .builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "message", SourceLocation.ofFile("Order.java"))
            .build();

    @Test
    void aSuccessfulRunCarriesItsFindings() {
        ProviderRunResult result = ProviderRunResult.success(List.of(FINDING));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.findings()).containsExactly(FINDING);
    }

    @Test
    void aSuccessfulRunMayHaveNoFindings() {
        ProviderRunResult result = ProviderRunResult.success(List.of());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void aFailedRunHasNoFindingsButCarriesAReason() {
        ProviderRunResult result = ProviderRunResult.failure("sonar-scanner binary not found on PATH");

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.findings()).isEmpty();
        assertThat(result.failureReason()).isEqualTo("sonar-scanner binary not found on PATH");
    }

    @Test
    void rejectsABlankFailureReason() {
        assertThatThrownBy(() -> ProviderRunResult.failure("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
