package com.athena.semantic;

import com.athena.analysis.spi.ExternalFinding;
import com.athena.analysis.spi.ExternalFindingSeverity;
import com.athena.analysis.spi.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link AnalysisResult}'s external-findings
 * accessors (ticket #114/#149) — {@link
 * com.athena.plugins.ExternalFindingsAsEvidenceSteps} already covers the
 * end-to-end path through a real {@link PrAnalyzer} run; this focuses on
 * {@link AnalysisResult} itself, constructed directly (its constructor is
 * package-private, callable only from here and {@link PrAnalyzer}).
 */
class AnalysisResultExternalFindingsTest {

    private static ExternalFinding findingOn(String filePath) {
        return ExternalFinding
                .builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "message", SourceLocation.ofFile(filePath))
                .build();
    }

    @Test
    void carriesEveryConfiguredProvidersFindings() {
        ExternalFinding finding = findingOn("Order.java");
        AnalysisResult result = new AnalysisResult(AnalysisStatus.READY, List.of(), List.of(), List.of(), Map.of(),
                List.of(finding));

        assertThat(result.externalFindings()).containsExactly(finding);
    }

    @Test
    void anAnalysisWithNoProvidersConfiguredHasNoExternalFindings() {
        AnalysisResult result = new AnalysisResult(AnalysisStatus.READY, List.of(), List.of(), List.of(), Map.of(),
                List.of());

        assertThat(result.externalFindings()).isEmpty();
    }

    @Test
    void findingsForAFileOnlyIncludeThatFilesFindings() {
        ExternalFinding orderFinding = findingOn("Order.java");
        ExternalFinding paymentFinding = findingOn("Payment.java");
        AnalysisResult result = new AnalysisResult(AnalysisStatus.READY, List.of(), List.of(), List.of(), Map.of(),
                List.of(orderFinding, paymentFinding));

        assertThat(result.externalFindingsFor("Order.java")).containsExactly(orderFinding);
    }

    @Test
    void findingsForAFileWithNoFindingsIsEmpty() {
        AnalysisResult result = new AnalysisResult(AnalysisStatus.READY, List.of(), List.of(), List.of(), Map.of(),
                List.of(findingOn("Order.java")));

        assertThat(result.externalFindingsFor("Untouched.java")).isEmpty();
    }
}
