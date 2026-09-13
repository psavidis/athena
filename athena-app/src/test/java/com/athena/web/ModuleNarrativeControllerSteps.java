package com.athena.web;

import com.athena.ai.FakeModuleNarrativeProvider;
import io.cucumber.java.en.And;
import io.cucumber.java.en.But;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for the module-narrative endpoints (see {@link ModuleNarrativeController}),
 * reusing {@link ChangeMapControllerSteps}' "reviewer has selected a PR
 * whose base and head revisions differ by a rename" fixture the same way
 * {@link ChangeDetailControllerSteps}-style step classes do (its own doc
 * comment: shared fixture, one glue class per feature file to avoid
 * Cucumber-JVM's duplicate-step-definition error).
 */
public class ModuleNarrativeControllerSteps {

    private final ChangeMapControllerSteps sharedSteps;
    private final FakeModuleNarrativeProvider fakeProvider = new FakeModuleNarrativeProvider();

    private List<ModuleNarrativeResponse> moduleListResponse;
    private ModuleNarrativeResponse narrativeResponse;
    private ResponseStatusException failure;
    private String moduleUnderTest;
    private boolean providerConfigured = true;

    public ModuleNarrativeControllerSteps(ChangeMapControllerSteps sharedSteps) {
        this.sharedSteps = sharedSteps;
    }

    @And("an AI narrative provider is configured that explains a module as {string}")
    public void an_ai_narrative_provider_is_configured(String narrative) {
        fakeProvider.willReturnNarrative(narrative);
        providerConfigured = true;
    }

    @But("no AI narrative provider is configured")
    public void no_ai_narrative_provider_is_configured() {
        providerConfigured = false;
    }

    @When("the reviewer requests the module groups")
    public void the_reviewer_requests_the_module_groups() {
        moduleListResponse = controller().modules();
    }

    @Then("the response includes a module group covering the rename Change")
    public void the_response_includes_a_module_group_covering_the_rename_change() {
        assertThat(moduleListResponse).isNotEmpty();
        assertThat(moduleListResponse).anySatisfy(module -> assertThat(module.changeKeys()).isNotEmpty());
    }

    @Then("no AI narrative call was made")
    public void no_ai_narrative_call_was_made() {
        assertThat(fakeProvider.callCount()).isZero();
    }

    @When("the reviewer requests the narrative for that module")
    public void the_reviewer_requests_the_narrative_for_that_module() {
        moduleUnderTest = firstModuleName();
        requestNarrative(moduleUnderTest);
    }

    @When("the reviewer requests the narrative for that module twice")
    public void the_reviewer_requests_the_narrative_for_that_module_twice() {
        moduleUnderTest = firstModuleName();
        requestNarrative(moduleUnderTest);
        requestNarrative(moduleUnderTest);
    }

    @When("the reviewer requests the narrative for module {string}")
    public void the_reviewer_requests_the_narrative_for_module(String moduleName) {
        requestNarrative(moduleName);
    }

    private String firstModuleName() {
        return new ModuleNarrativeController(sharedSteps.session(), fakeProvider).modules().get(0).moduleName();
    }

    private void requestNarrative(String moduleName) {
        try {
            narrativeResponse = controller().narrativeFor(moduleName);
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    private ModuleNarrativeController controller() {
        return new ModuleNarrativeController(sharedSteps.session(), providerConfigured ? fakeProvider : null);
    }

    @Then("the narrative response says {string}")
    public void the_narrative_response_says(String expectedNarrative) {
        assertThat(narrativeResponse.narrative()).isEqualTo(expectedNarrative);
    }

    @Then("the AI narrative provider was called only once")
    public void the_ai_narrative_provider_was_called_only_once() {
        assertThat(fakeProvider.callCount()).isEqualTo(1);
    }

    @Then("the request is rejected as service unavailable")
    public void the_request_is_rejected_as_service_unavailable() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().value()).isEqualTo(503);
    }

    @Then("the request is rejected because the module was not found")
    public void the_request_is_rejected_because_the_module_was_not_found() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().value()).isEqualTo(404);
    }
}
