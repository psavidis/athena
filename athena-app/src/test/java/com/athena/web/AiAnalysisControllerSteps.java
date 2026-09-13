package com.athena.web;

import com.athena.ai.FakeAiProvider;
import com.athena.web.controller.AiAnalysisController;
import com.athena.web.response.AiContextBoundaryResponse;
import com.athena.web.response.AiFindingResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for the AI context boundary & findings review (ticket #77). Reuses
 * {@link ChangeMapControllerSteps}'s "reviewer has selected a PR with a
 * rename" fixture (same session, same rename Change) via constructor
 * injection, per its established shared-steps-class convention.
 */
public class AiAnalysisControllerSteps {

    private final ChangeMapControllerSteps changeMapSteps;
    // Fake AI provider: real network calls to Claude's API are an external
    // system boundary tests can't/shouldn't cross for real.
    private final FakeAiProvider provider = new FakeAiProvider();

    private AiAnalysisController controller;
    private AiContextBoundaryResponse boundaryResponse;
    private List<AiFindingResponse> findingsResponse;

    public AiAnalysisControllerSteps(ChangeMapControllerSteps changeMapSteps) {
        this.changeMapSteps = changeMapSteps;
    }

    private AiAnalysisController controller() {
        if (controller == null) {
            controller = new AiAnalysisController(changeMapSteps.session(), provider);
        }
        return controller;
    }

    @Given("the configured AI provider will return a finding {string} related to the rename Change")
    public void the_provider_will_return_a_finding_related_to_the_rename_change(String description) {
        provider.willReturnFinding(description, description, changeMapSteps.renameChangeTitle());
    }

    @Given("the configured AI provider will return a general finding {string}")
    public void the_provider_will_return_a_general_finding(String description) {
        provider.willReturnFinding(description, description);
    }

    @When("the reviewer requests the AI context boundary via the API")
    public void the_reviewer_requests_the_ai_context_boundary_via_the_api() {
        try {
            boundaryResponse = controller().contextBoundary();
        } catch (ResponseStatusException e) {
            changeMapSteps.recordFailure(e);
        }
    }

    @When("the reviewer triggers AI analysis via the API")
    public void the_reviewer_triggers_ai_analysis_via_the_api() {
        triggerAnalysis();
    }

    @Given("the reviewer has triggered AI analysis")
    public void the_reviewer_has_triggered_ai_analysis() {
        triggerAnalysis();
    }

    private void triggerAnalysis() {
        try {
            findingsResponse = controller().triggerAnalysis();
        } catch (ResponseStatusException e) {
            changeMapSteps.recordFailure(e);
        }
    }

    @When("the reviewer accepts the finding {string}")
    public void the_reviewer_accepts_the_finding(String description) {
        findingsResponse = controller().accept(findingIdFor(description));
    }

    @When("the reviewer dismisses the finding {string}")
    public void the_reviewer_dismisses_the_finding(String description) {
        findingsResponse = controller().dismiss(findingIdFor(description));
    }

    @When("the reviewer accepts a finding id that was never returned")
    public void the_reviewer_accepts_a_finding_id_that_was_never_returned() {
        try {
            controller().accept("no-such-finding-id");
        } catch (ResponseStatusException e) {
            changeMapSteps.recordFailure(e);
        }
    }

    private String findingIdFor(String description) {
        return findingsResponse.stream()
                .filter(finding -> finding.description().equals(description))
                .findFirst()
                .orElseThrow()
                .id();
    }

    @Then("the boundary response lists the rename Change among the included Changes")
    public void the_boundary_response_lists_the_rename_change_among_the_included_changes() {
        assertThat(boundaryResponse.includedChangeTitles()).anySatisfy(title -> assertThat(title).contains("Rename"));
    }

    @Then("the boundary response confirms private notes are excluded")
    public void the_boundary_response_confirms_private_notes_are_excluded() {
        assertThat(boundaryResponse.privateNotesExcluded()).isTrue();
    }

    @Then("the findings response includes {string}")
    public void the_findings_response_includes(String description) {
        assertThat(findingsResponse).anySatisfy(finding -> assertThat(finding.description()).isEqualTo(description));
    }

    @Then("that finding is pending")
    public void that_finding_is_pending() {
        assertThat(findingsResponse).allSatisfy(finding ->
                assertThat(finding.disposition().name()).isEqualTo("PENDING"));
    }

    @Then("that finding's jump target is the rename Change")
    public void that_findings_jump_target_is_the_rename_change() {
        assertThat(findingsResponse.get(0).jumpTargetChangeKey()).isEqualTo(changeMapSteps.renameChangeKey());
    }

    @Then("that finding has no jump target")
    public void that_finding_has_no_jump_target() {
        assertThat(findingsResponse.get(0).jumpTargetChangeKey()).isNull();
    }

    @Then("the finding {string} is accepted")
    public void the_finding_is_accepted(String description) {
        assertDisposition(description, "ACCEPTED");
    }

    @Then("the finding {string} is dismissed")
    public void the_finding_is_dismissed(String description) {
        assertDisposition(description, "DISMISSED");
    }

    @Then("the finding {string} is still pending")
    public void the_finding_is_still_pending(String description) {
        assertDisposition(description, "PENDING");
    }

    private void assertDisposition(String description, String expected) {
        assertThat(findingsResponse)
                .filteredOn(finding -> finding.description().equals(description))
                .singleElement()
                .satisfies(finding -> assertThat(finding.disposition().name()).isEqualTo(expected));
    }
}
