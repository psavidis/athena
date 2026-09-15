package com.athena.plugins;

import com.athena.analysis.spi.ExternalFinding;
import com.athena.analysis.spi.ExternalFindingSeverity;
import com.athena.analysis.spi.ProviderConfiguration;
import com.athena.analysis.spi.ProviderRunResult;
import net.sourceforge.pmd.lang.rule.RulePriority;
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
 * Dedicated unit test for {@link PmdAnalysisProvider} (ticket #116). The
 * pmd_analysis_provider.feature Gherkin scenarios cover the black-box path through
 * {@code PrAnalyzer}; this exercises the provider directly against a real PMD run —
 * per CODE_STYLE.md/qa-ticket's Detroit-school rule, PMD is a real, in-process
 * library call, not a network/non-deterministic boundary, so it is never mocked here
 * — plus edge cases the Gherkin scenarios don't reach: the exhaustive severity
 * mapping and a missing source root.
 */
class PmdAnalysisProviderTest {

    private final PmdAnalysisProvider provider = new PmdAnalysisProvider();
    private Path sourceRoot;

    @BeforeEach
    void createSourceRoot() throws IOException {
        sourceRoot = Files.createTempDirectory("athena-pmd-provider");
    }

    @AfterEach
    void cleanUpSourceRoot() throws IOException {
        deleteRecursively(sourceRoot);
    }

    @Test
    void providerIdentifiesItselfAsPmdAnalyzingJava() {
        assertThat(provider.providerId()).isEqualTo("pmd");
        assertThat(provider.language()).isEqualTo("java");
    }

    @Test
    void aRealPmdViolationIsMappedToAnExternalFinding() throws IOException {
        writeJavaFile("Sample.java", "package sample;\n\n"
                + "public class Sample {\n"
                + "    public void run() {\n"
                + "        int total = 42;\n"
                + "    }\n"
                + "}\n");

        ProviderRunResult result = provider.analyze(sourceRoot, List.of(), ProviderConfiguration.enabled(Map.of()));

        assertThat(result.isSuccessful()).isTrue();
        ExternalFinding finding = result.findings().stream()
                .filter(f -> f.ruleId().equals("UnusedLocalVariable"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected an UnusedLocalVariable finding, got: " + result.findings()));

        assertThat(finding.providerId()).isEqualTo("pmd");
        assertThat(finding.language()).isEqualTo("java");
        assertThat(finding.ruleName()).contains("UnusedLocalVariable");
        assertThat(finding.message()).isNotBlank();
        assertThat(finding.severity()).isNotNull();
        assertThat(finding.location().filePath()).isEqualTo("Sample.java");
        assertThat(finding.location().startLine()).contains(5);
        assertThat(finding.providerSpecificMetadata()).containsKey("ruleSet");
    }

    @Test
    void cleanCodeProducesNoFindings() throws IOException {
        writeJavaFile("Clean.java", "package sample;\n\n"
                + "public class Clean {\n"
                + "    public int add(int left, int right) {\n"
                + "        return left + right;\n"
                + "    }\n"
                + "}\n");

        ProviderRunResult result = provider.analyze(sourceRoot, List.of(), ProviderConfiguration.enabled(Map.of()));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void aRulesetThatCannotBeLoadedProducesAFailedResultRatherThanThrowing() {
        ProviderRunResult result = provider.analyze(sourceRoot, List.of(),
                ProviderConfiguration.enabled(Map.of(PmdAnalysisProvider.RULESET_SETTING, "rulesets/java/does-not-exist.xml")));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).isNotBlank();
    }

    @Test
    void aMissingSourceRootProducesAFailedResultRatherThanThrowing() {
        Path missing = sourceRoot.resolve("does-not-exist");

        ProviderRunResult result = provider.analyze(missing, List.of(), ProviderConfiguration.enabled(Map.of()));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).isNotBlank();
    }

    @Test
    void everyPmdRulePriorityMapsToADocumentedSeverity() {
        assertThat(PmdAnalysisProvider.severityOf(RulePriority.HIGH)).isEqualTo(ExternalFindingSeverity.BLOCKER);
        assertThat(PmdAnalysisProvider.severityOf(RulePriority.MEDIUM_HIGH)).isEqualTo(ExternalFindingSeverity.HIGH);
        assertThat(PmdAnalysisProvider.severityOf(RulePriority.MEDIUM)).isEqualTo(ExternalFindingSeverity.MEDIUM);
        assertThat(PmdAnalysisProvider.severityOf(RulePriority.MEDIUM_LOW)).isEqualTo(ExternalFindingSeverity.LOW);
        assertThat(PmdAnalysisProvider.severityOf(RulePriority.LOW)).isEqualTo(ExternalFindingSeverity.INFO);
    }

    private void writeJavaFile(String fileName, String content) throws IOException {
        Files.writeString(sourceRoot.resolve(fileName), content);
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
