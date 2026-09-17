package com.athena.web.reviewbriefing;

import com.athena.git.TempDirectories;
import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.knowledge.KnowledgeProviderResolver;
import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewbriefing.BriefingItem;
import com.athena.reviewbriefing.FakeSemanticChangeSummaryProvider;
import com.athena.reviewbriefing.FakeUncertaintyAndQuestionsProvider;
import com.athena.reviewbriefing.UncertaintyAndQuestions;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.JavaFixtureSupport;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Review Briefing composition (ticket #223's own
 * backend prerequisite). Detroit-school against a real {@link WebSession}
 * and {@link ReviewBriefingController}, with a real {@link
 * GitHubRepositoryProvider} over {@link FakeGitHubTransport} (the network
 * boundary) and fake AI providers (the AI-network boundary), matching
 * this session's established conventions throughout.
 */
public class ReviewBriefingControllerSteps {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final FakeSemanticChangeSummaryProvider summaryProvider = new FakeSemanticChangeSummaryProvider();
    private final FakeUncertaintyAndQuestionsProvider uncertaintyProvider = new FakeUncertaintyAndQuestionsProvider();

    private Path baseRoot;
    private Path headRoot;
    private ReviewBriefingController controller;
    private ReviewBriefingResponse response;
    private ResponseStatusException failure;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-review-briefing-controller-base-");
        headRoot = Files.createTempDirectory("athena-review-briefing-controller-head-");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a PR with a detected Change and generators that produce real briefing content")
    public void a_pr_with_a_detected_change_and_generators_that_produce_real_content() {
        transport.acceptToken(TOKEN, "octocat");
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        ImportedPullRequest pr = new ImportedPullRequest(42, "A PR", "author", "base-sha", "head-sha", List.of(), List.of());
        session.connect(TOKEN);
        session.select(new WebSession.SelectedPullRequest(
                pr, REPOSITORY, headRoot, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));

        summaryProvider.willReturnSummary("Renamed a method on Greeter.");
        uncertaintyProvider.willReturn(new UncertaintyAndQuestions(
                List.of(BriefingItem.of("Unclear why this was renamed", "Greeter")), List.of()));

        controller = new ReviewBriefingController(session, token -> new GitHubRepositoryProvider(token, transport),
                new KnowledgeProviderResolver(), summaryProvider, uncertaintyProvider);
    }

    @Given("no PR is selected")
    public void no_pr_is_selected() {
        transport.acceptToken(TOKEN, "octocat");
        session.connect(TOKEN);
        controller = new ReviewBriefingController(session, token -> new GitHubRepositoryProvider(token, transport),
                new KnowledgeProviderResolver(), summaryProvider, uncertaintyProvider);
    }

    @When("a developer requests the Review Briefing")
    public void a_developer_requests_the_review_briefing() {
        try {
            response = controller.reviewBriefing();
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the Review Briefing's change summary is present")
    public void the_review_briefings_change_summary_is_present() {
        assertThat(response.changeSummary()).isNotNull();
    }

    @Then("the Review Briefing has at least {int} focus area")
    @Then("the Review Briefing has at least {int} focus areas")
    public void the_review_briefing_has_at_least_n_focus_areas(int min) {
        assertThat(response.focusAreas()).hasSizeGreaterThanOrEqualTo(min);
    }

    @Then("the Review Briefing request is rejected as invalid")
    public void the_review_briefing_request_is_rejected_as_invalid() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().is4xxClientError()).isTrue();
    }
}
