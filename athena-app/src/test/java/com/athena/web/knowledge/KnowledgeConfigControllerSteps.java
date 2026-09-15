package com.athena.web.knowledge;

import com.athena.git.TempDirectories;
import com.athena.knowledge.KnowledgeFixtureSteps;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for the Knowledge Provider configuration feature (ticket #118). */
public class KnowledgeConfigControllerSteps {

    private final KnowledgeFixtureSteps fixture;

    private Path vaultDir;
    private KnowledgeConfigController controller;
    private KnowledgeConfigController.KnowledgeStatusResponse status;
    private ResponseStatusException failure;

    public KnowledgeConfigControllerSteps(KnowledgeFixtureSteps fixture) {
        this.fixture = fixture;
    }

    /**
     * Built lazily, on first actual use from a step — never eagerly in the constructor or an
     * {@code @Before} hook, since {@link KnowledgeFixtureSteps}' own {@code @Before} (which
     * initializes its store) is not guaranteed to have run yet at either of those points, only
     * by the time a step itself executes.
     */
    private KnowledgeConfigController controller() {
        if (controller == null) {
            controller = new KnowledgeConfigController(fixture.store());
        }
        return controller;
    }

    @After
    public void cleanUp() {
        if (vaultDir != null) {
            TempDirectories.deleteRecursively(vaultDir);
        }
    }

    @Given("an Obsidian vault at a real directory on disk")
    public void an_obsidian_vault_at_a_real_directory_on_disk() throws IOException {
        vaultDir = Files.createTempDirectory("athena-obsidian-vault-");
    }

    @When("the user checks the Knowledge Provider status")
    public void the_user_checks_the_knowledge_provider_status() {
        status = controller().status();
    }

    @When("the user configures that directory as the Obsidian Knowledge Provider")
    public void the_user_configures_that_directory_as_the_obsidian_knowledge_provider() {
        connect(vaultDir.toString());
    }

    @When("the user configures a nonexistent directory as the Obsidian Knowledge Provider")
    public void the_user_configures_a_nonexistent_directory_as_the_obsidian_knowledge_provider() {
        connect(Path.of(System.getProperty("java.io.tmpdir"), "athena-does-not-exist-" + System.nanoTime()).toString());
    }

    @When("the user disconnects the Knowledge Provider")
    public void the_user_disconnects_the_knowledge_provider() {
        status = controller().disconnectObsidian();
    }

    @Then("the Knowledge Provider is reported as not configured")
    public void the_knowledge_provider_is_reported_as_not_configured() {
        if (status == null) {
            status = controller().status();
        }
        assertThat(status.configured()).isFalse();
    }

    @Then("no error is reported")
    public void no_error_is_reported() {
        assertThat(failure).isNull();
    }

    @Then("the Knowledge Provider is reported as configured with provider {string}")
    public void the_knowledge_provider_is_reported_as_configured_with_provider(String providerId) {
        assertThat(status.configured()).isTrue();
        assertThat(status.providerId()).isEqualTo(providerId);
    }

    @Then("the Knowledge Provider status shows the configured vault path")
    public void the_knowledge_provider_status_shows_the_configured_vault_path() {
        assertThat(status.vaultPath()).isEqualTo(vaultDir.toString());
    }

    @Then("the configuration attempt is rejected")
    public void the_configuration_attempt_is_rejected() {
        assertThat(failure).isNotNull();
    }

    @Then("the Knowledge Provider is still reported as not configured")
    public void the_knowledge_provider_is_still_reported_as_not_configured() {
        status = controller().status();
        assertThat(status.configured()).isFalse();
    }

    private void connect(String vaultPath) {
        try {
            status = controller().connectObsidian(new KnowledgeConfigController.ConnectObsidianRequest(vaultPath));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }
}
