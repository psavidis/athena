package com.athena.web.reviewui;

import com.athena.git.TempDirectories;
import com.athena.github.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.Change;
import com.athena.semantic.ReviewStateStore;
import com.athena.semantic.SemanticDimension;
import com.athena.web.ChangeKey;
import com.athena.web.ChangeKeyFixture;
import com.athena.web.WebSession;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for the Semantic Profile endpoint (ticket #94), reusing
 * {@link ChangeMapControllerSteps}' shared session/rename-Change fixture via
 * constructor injection, per its established shared-steps-class convention
 * (see that class's own doc comment). Its "heuristic classification"
 * scenario needs a different fixture (an added, not renamed, class), built
 * directly against plain base/head directories the same way
 * {@code PrAnalyzerSemanticProfileTest} does — no git checkout is needed
 * since {@code PrAnalyzer.analyze} only ever reads two directory trees.
 */
public class SemanticProfileControllerSteps {

    private final ChangeMapControllerSteps sharedSteps;

    private Path baseRoot;
    private Path headRoot;
    private Path workDir;
    private String newlyAddedChangeKey;

    private SemanticProfileResponse response;
    private SemanticDimensionEntryResponse matchedEntry;

    public SemanticProfileControllerSteps(ChangeMapControllerSteps sharedSteps) {
        this.sharedSteps = sharedSteps;
    }

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-semantic-profile-base-");
        headRoot = Files.createTempDirectory("athena-semantic-profile-head-");
        workDir = Files.createTempDirectory("athena-semantic-profile-work-");
    }

    @After
    public void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(baseRoot);
        TempDirectories.deleteRecursively(headRoot);
        TempDirectories.deleteRecursively(workDir);
    }

    @Given("the reviewer has selected a PR whose head revision adds a class named {string}")
    public void the_reviewer_has_selected_a_pr_whose_head_revision_adds_a_class_named(String simpleName)
            throws IOException {
        Files.writeString(headRoot.resolve(simpleName + ".java"), "public class " + simpleName + " {\n}\n");

        ImportedPullRequest pr = new ImportedPullRequest(1, "Add " + simpleName, "author", "base", "head",
                List.of(), List.of());
        sharedSteps.session().select(new WebSession.SelectedPullRequest(
                pr, "acme/widgets", workDir, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));

        Change change = sharedSteps.session().selectedPullRequest().orElseThrow().changes().get(0);
        newlyAddedChangeKey = ChangeKey.encode(change);
    }

    @When("the reviewer requests the Semantic Profile for the rename Change")
    public void the_reviewer_requests_the_semantic_profile_for_the_rename_change() {
        requestSemanticProfile(sharedSteps.renameChangeKey());
    }

    @When("the reviewer requests the Semantic Profile for the newly-added Change")
    public void the_reviewer_requests_the_semantic_profile_for_the_newly_added_change() {
        requestSemanticProfile(newlyAddedChangeKey);
    }

    @When("the reviewer requests the Semantic Profile for a Change")
    public void the_reviewer_requests_the_semantic_profile_for_a_change() {
        requestSemanticProfile("anything");
    }

    @When("the reviewer requests the Semantic Profile for an unknown Change")
    public void the_reviewer_requests_the_semantic_profile_for_an_unknown_change() {
        requestSemanticProfile(ChangeKeyFixture.unmatched());
    }

    private void requestSemanticProfile(String changeKey) {
        try {
            response = new SemanticProfileController(sharedSteps.session()).semanticProfile(changeKey);
        } catch (ResponseStatusException e) {
            sharedSteps.recordFailure(e);
        }
    }

    @Then("the Semantic Profile includes a(n) {string} entry for the {string} concept")
    public void the_semantic_profile_includes_an_entry_for_the_concept(String dimensionLabel, String conceptName) {
        SemanticDimension dimension = SemanticDimension.valueOf(dimensionLabel.toUpperCase(Locale.ROOT));
        matchedEntry = response.dimensions().stream()
                .filter(entry -> entry.dimension() == dimension)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + dimensionLabel + " entry in Semantic Profile response"));
        assertThat(matchedEntry.conceptName()).isEqualTo(conceptName);
    }

    @Then("that entry is marked Observed with 100% confidence")
    public void that_entry_is_marked_observed_with_100_percent_confidence() {
        assertThat(matchedEntry.inferred()).isFalse();
        assertThat(matchedEntry.confidencePercent()).isEqualTo(100);
    }

    @Then("that entry is marked Inferred with a confidence below 100%")
    public void that_entry_is_marked_inferred_with_a_confidence_below_100_percent() {
        assertThat(matchedEntry.inferred()).isTrue();
        assertThat(matchedEntry.confidencePercent()).isLessThan(100);
    }

    @Then("that entry includes supporting evidence")
    public void that_entry_includes_supporting_evidence() {
        assertThat(matchedEntry.evidence()).isNotEmpty();
    }

    @Then("that entry lists {string} as a supporting structural change")
    public void that_entry_lists_as_a_supporting_structural_change(String conceptName) {
        assertThat(matchedEntry.supportingConceptNames()).contains(conceptName);
    }

    @Then("the Semantic Profile has no {string} entry")
    public void the_semantic_profile_has_no_entry(String dimensionLabel) {
        SemanticDimension dimension = SemanticDimension.valueOf(dimensionLabel.toUpperCase(Locale.ROOT));
        assertThat(response.dimensions())
                .extracting(SemanticDimensionEntryResponse::dimension)
                .doesNotContain(dimension);
    }
}
