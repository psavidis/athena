package com.athena.plugins;

import com.athena.analysis.spi.ExternalAnalysisProvider;
import com.athena.analysis.spi.ProviderConfiguration;
import com.athena.semantic.AnalysisResult;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.TransformationKind;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalFindingsAsEvidenceSteps {

    private Path baseRoot;
    private Path headRoot;
    private final Map<ExternalAnalysisProvider, ProviderConfiguration> providers = new LinkedHashMap<>();
    private AnalysisResult result;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-external-findings-base");
        headRoot = Files.createTempDirectory("athena-external-findings-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Given("a provider {string} configured to report a finding on {string}")
    public void a_provider_configured_to_report_a_finding_on(String providerId, String filePath) {
        registerProvider(new FakeExternalAnalysisProvider(providerId, "java").reportingOn(filePath));
    }

    @Given("a provider {string} configured to report findings on {string} and {string}")
    public void a_provider_configured_to_report_findings_on_two_files(String providerId, String first, String second) {
        registerProvider(new FakeExternalAnalysisProvider(providerId, "java").reportingOn(first).reportingOn(second));
    }

    @Given("a provider {string} configured to fail with {string}")
    public void a_provider_configured_to_fail_with(String providerId, String reason) {
        registerProvider(new FakeExternalAnalysisProvider(providerId, "java").failingWith(reason));
    }

    @Given("no external providers are configured")
    public void no_external_providers_are_configured() {
        providers.clear();
    }

    @Given("PMD is configured as a provider")
    public void pmd_is_configured_as_a_provider() {
        registerProvider(new PmdAnalysisProvider());
    }

    @Given("PMD is configured with a ruleset it cannot load")
    public void pmd_is_configured_with_a_ruleset_it_cannot_load() {
        registerProvider(new PmdAnalysisProvider(),
                Map.of(PmdAnalysisProvider.RULESET_SETTING, "rulesets/java/does-not-exist.xml"));
    }

    @Given("a Java class {string} with an unused local variable")
    public void a_java_class_with_an_unused_local_variable(String fileName) {
        writeFile(headRoot, fileName, "package sample;\n\n"
                + "public class " + baseName(fileName) + " {\n"
                + "    public void run() {\n"
                + "        int total = 42;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a Java class {string} with no PMD-detectable issues")
    public void a_java_class_with_no_pmd_detectable_issues(String fileName) {
        writeFile(headRoot, fileName, "package sample;\n\n"
                + "public class " + baseName(fileName) + " {\n"
                + "    public int add(int left, int right) {\n"
                + "        return left + right;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base and head revision where a symbol is renamed")
    public void a_base_and_head_revision_where_a_symbol_is_renamed() {
        the_pr_renames_a_symbol();
    }

    @Given("the PR renames a symbol between its base and head revisions")
    public void the_pr_renames_a_symbol() {
        write(baseRoot, "Guard", "public class Guard {\n"
                + "    public boolean isAllowed() { return true; }\n"
                + "}\n");
        write(headRoot, "Guard", "public class Guard {\n"
                + "    public boolean isPermitted() { return true; }\n"
                + "}\n");
    }

    @When("the PR is analyzed")
    public void the_pr_is_analyzed() {
        PrAnalyzer prAnalyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins(), providers);
        result = prAnalyzer.analyze(baseRoot, headRoot);
    }

    @When("the PR is analyzed with PMD configured as a provider")
    public void the_pr_is_analyzed_with_pmd_configured_as_a_provider() {
        the_pr_is_analyzed();
    }

    @Then("the analysis result includes a finding from {string} on {string}")
    public void the_analysis_result_includes_a_finding_from_on(String providerId, String filePath) {
        assertThat(result.externalFindings())
                .anyMatch(finding -> finding.providerId().equals(providerId) && finding.location().filePath().equals(filePath));
    }

    @Then("the analysis result's findings for {string} only include the {string} finding")
    public void the_analysis_results_findings_for_only_include(String queriedFile, String expectedFile) {
        assertThat(result.externalFindingsFor(queriedFile))
                .extracting(finding -> finding.location().filePath())
                .containsOnly(expectedFile);
    }

    @Then("the analysis result includes no external findings")
    public void the_analysis_result_includes_no_external_findings() {
        assertThat(result.externalFindings()).isEmpty();
    }

    @Then("the analysis result includes no finding from {string}")
    public void the_analysis_result_includes_no_finding_from(String providerId) {
        assertThat(result.externalFindings()).noneMatch(finding -> finding.providerId().equals(providerId));
    }

    @Then("the analysis result still detects the rename as a Change")
    public void the_analysis_result_still_detects_the_rename() {
        assertThat(result.changes()).anyMatch(change -> change.kind() == TransformationKind.RENAME_SYMBOL);
    }

    @Then("the analysis result still classifies the renamed symbol as a Change")
    public void the_analysis_result_still_classifies_the_renamed_symbol() {
        the_analysis_result_still_detects_the_rename();
    }

    @Then("the analysis result's Change classification is unaffected by the external finding")
    public void the_analysis_results_change_classification_is_unaffected() {
        assertThat(result.changes()).anyMatch(change -> change.kind() == TransformationKind.RENAME_SYMBOL);
        assertThat(result.semanticProfiles()).hasSameSizeAs(result.changes());
    }

    private void registerProvider(ExternalAnalysisProvider provider) {
        registerProvider(provider, Map.of());
    }

    private void registerProvider(ExternalAnalysisProvider provider, Map<String, String> settings) {
        providers.put(provider, ProviderConfiguration.enabled(settings));
    }

    private void write(Path root, String className, String content) {
        writeFile(root, className + ".java", content);
    }

    private void writeFile(Path root, String fileName, String content) {
        try {
            Files.writeString(root.resolve(fileName), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
