package com.athena.plugins;

import com.athena.analysis.spi.ExternalFinding;
import com.athena.analysis.spi.ExternalFindingSeverity;
import com.athena.analysis.spi.ProviderConfiguration;
import com.athena.analysis.spi.ProviderRunResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link ESLintAnalysisProvider} (ticket #117). The
 * eslint_analysis_provider.feature Gherkin scenarios cover the black-box path through
 * {@code PrAnalyzer}; this exercises the provider directly against a real ESLint CLI
 * process — per CODE_STYLE.md/qa-ticket's Detroit-school rule, a real subprocess run
 * of a real, pinned ESLint install is a genuine collaborator, never mocked here (see
 * {@link EslintTestFixture}) — plus edge cases the Gherkin scenarios don't reach: the
 * exhaustive severity mapping, a missing source root, and a misconfigured ESLint run.
 *
 * <p>ESLint is installed once for the whole class ({@link #installEslintOnce}) and
 * referenced from each test via {@link ESLintAnalysisProvider#ESLINT_EXECUTABLE_SETTING}
 * — the provider's own documented override hook — rather than re-installing per test;
 * this is purely a shared-fixture speed optimization, distinct from the provider's
 * default resolution (each analyzed project's own {@code node_modules/.bin/eslint}),
 * which {@link #eslintNotInstalledProducesAFailedResultRatherThanThrowing} exercises
 * directly.
 */
class ESLintAnalysisProviderTest {

    private static Path sharedEslintInstall;
    private static Path eslintExecutable;

    private final ESLintAnalysisProvider provider = new ESLintAnalysisProvider();
    private Path sourceRoot;

    @BeforeAll
    static void installEslintOnce() throws IOException, InterruptedException {
        sharedEslintInstall = Files.createTempDirectory("athena-eslint-shared-install");
        EslintTestFixture.installInto(sharedEslintInstall);
        eslintExecutable = sharedEslintInstall.resolve("node_modules").resolve(".bin").resolve("eslint");
    }

    @AfterAll
    static void cleanUpSharedInstall() throws IOException {
        deleteRecursively(sharedEslintInstall);
    }

    @BeforeEach
    void createSourceRoot() throws IOException {
        sourceRoot = Files.createTempDirectory("athena-eslint-provider");
    }

    @AfterEach
    void cleanUpSourceRoot() throws IOException {
        deleteRecursively(sourceRoot);
    }

    @Test
    void providerIdentifiesItselfAsESLintAnalyzingJavaScript() {
        assertThat(provider.providerId()).isEqualTo("eslint");
        assertThat(provider.language()).isEqualTo("javascript");
    }

    @Test
    void aRealEslintViolationIsMappedToAnExternalFinding() throws IOException {
        writeNoUnusedVarsConfig();
        writeJsFile("sample.js", "function run() {\n"
                + "  var total = 42;\n"
                + "  return 1;\n"
                + "}\n\n"
                + "module.exports = { run };\n");

        ProviderRunResult result = provider.analyze(sourceRoot, List.of(), configurationWithSharedExecutable());

        assertThat(result.isSuccessful()).isTrue();
        ExternalFinding finding = result.findings().stream()
                .filter(f -> f.ruleId().equals("no-unused-vars"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a no-unused-vars finding, got: " + result.findings()));

        assertThat(finding.providerId()).isEqualTo("eslint");
        assertThat(finding.language()).isEqualTo("javascript");
        assertThat(finding.ruleName()).contains("no-unused-vars");
        assertThat(finding.message()).isNotBlank();
        assertThat(finding.severity()).isEqualTo(ExternalFindingSeverity.HIGH);
        assertThat(finding.location().filePath()).isEqualTo("sample.js");
        assertThat(finding.location().startLine()).contains(2);
        assertThat(finding.providerSpecificMetadata()).containsKey("column");
    }

    @Test
    void cleanCodeProducesNoFindings() throws IOException {
        writeNoUnusedVarsConfig();
        writeJsFile("clean.js", "function add(left, right) {\n"
                + "  return left + right;\n"
                + "}\n\n"
                + "module.exports = { add };\n");

        ProviderRunResult result = provider.analyze(sourceRoot, List.of(), configurationWithSharedExecutable());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void eslintNotInstalledProducesAFailedResultRatherThanThrowing() throws IOException {
        writeNoUnusedVarsConfig();
        writeJsFile("sample.js", "module.exports = {};\n");

        ProviderRunResult result = provider.analyze(sourceRoot, List.of(), ProviderConfiguration.enabled(Map.of()));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).isNotBlank();
    }

    @Test
    void aMissingSourceRootProducesAFailedResultRatherThanThrowing() {
        Path missing = sourceRoot.resolve("does-not-exist");

        ProviderRunResult result = provider.analyze(missing, List.of(), configurationWithSharedExecutable());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).isNotBlank();
    }

    @Test
    void aMisconfiguredEslintProducesAFailedResultRatherThanThrowing() throws IOException {
        Files.writeString(sourceRoot.resolve("eslint.config.js"), "this is not valid javascript {{{\n");
        writeJsFile("sample.js", "module.exports = {};\n");

        ProviderRunResult result = provider.analyze(sourceRoot, List.of(), configurationWithSharedExecutable());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).isNotBlank();
    }

    @Test
    void everyDocumentedEslintSeverityMapsToADocumentedSeverity() {
        assertThat(ESLintAnalysisProvider.severityOf(2)).isEqualTo(ExternalFindingSeverity.HIGH);
        assertThat(ESLintAnalysisProvider.severityOf(1)).isEqualTo(ExternalFindingSeverity.MEDIUM);
    }

    @Test
    void anUndocumentedEslintSeverityThrowsRatherThanGuessing() {
        assertThatThrownBy(() -> ESLintAnalysisProvider.severityOf(0)).isInstanceOf(IllegalStateException.class);
    }

    private void writeNoUnusedVarsConfig() throws IOException {
        Files.writeString(sourceRoot.resolve("eslint.config.js"), "module.exports = [\n"
                + "  {\n"
                + "    rules: {\n"
                + "      \"no-unused-vars\": \"error\"\n"
                + "    }\n"
                + "  }\n"
                + "];\n");
    }

    private void writeJsFile(String fileName, String content) throws IOException {
        Files.writeString(sourceRoot.resolve(fileName), content);
    }

    private ProviderConfiguration configurationWithSharedExecutable() {
        return ProviderConfiguration.enabled(
                Map.of(ESLintAnalysisProvider.ESLINT_EXECUTABLE_SETTING, eslintExecutable.toString()));
    }

    private static void deleteRecursively(Path root) throws IOException {
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
