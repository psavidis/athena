package com.athena.web.contextrewind;

import com.athena.contextrewind.ContextNarrativeProvider;
import com.athena.git.GitRevisionCheckout;
import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.repository.RepositoryProvider;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link ContextRewindController} (ticket #187) —
 * {@link com.athena.contextrewind.ContextRewindService} already has its own
 * focused unit tests (ticket #161) for the aggregation logic itself; this
 * covers the controller's own job: resolving the session's real selection,
 * delegating, and mapping to the response DTOs. A fake {@link RepositoryProvider}
 * (over the established {@link FakeGitHubTransport} network boundary) stands in
 * for GitHub; a real checked-out git repo backs the session's own selection,
 * the same way {@link com.athena.web.reviewui.ModuleTopologyControllerTest} does.
 *
 * <p>Unlike {@code ModuleTopologyController} (ticket #129), this controller has
 * no standalone-Diff fallback: {@link com.athena.contextrewind.ContextRewindRequest}
 * requires a repository, which only a selected Pull Request carries.
 */
class ContextRewindControllerTest {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final RepositoryProvider fakeRepositoryProvider = new GitHubRepositoryProvider(TOKEN, transport);
    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final ContextRewindController controller =
            new ContextRewindController(session, token -> fakeRepositoryProvider, null);

    private Path repoDir;
    private Path workDir;

    @BeforeEach
    void createTempRoots() throws IOException {
        transport.acceptToken(TOKEN, "octocat");
        repoDir = Files.createTempDirectory("athena-context-rewind-controller-repo-");
        workDir = Files.createTempDirectory("athena-context-rewind-controller-work-");
    }

    @AfterEach
    void cleanUpTempRoots() {
        com.athena.git.TempDirectories.deleteRecursively(repoDir);
        com.athena.git.TempDirectories.deleteRecursively(workDir);
    }

    @Test
    void rejectsWhenNotConnectedToGitHub() {
        assertThatThrownBy(() -> controller.contextRewind("PaymentProcessor", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void rejectsWhenNoPullRequestIsSelected() {
        session.connect(TOKEN);

        assertThatThrownBy(() -> controller.contextRewind("PaymentProcessor", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void rejectsAStandaloneDiffSinceContextRewindNeedsARepository() throws Exception {
        session.connect(TOKEN);
        initRepo();
        writeFile("README.md", "root\n");
        String baseSha = commit("Initial commit");
        writeFile("README.md", "root, updated\n");
        String headSha = commit("Second commit");
        selectStandaloneDiff(baseSha, headSha);

        assertThatThrownBy(() -> controller.contextRewind("PaymentProcessor", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void mapsTheReconstructedContextForTheSelectedPullRequestsEntity() throws Exception {
        session.connect(TOKEN);
        initRepo();
        writeFile("PaymentProcessor.java", "class PaymentProcessor {}\n");
        String baseSha = commit("Initial commit");
        writeFile("PaymentProcessor.java", "class PaymentProcessor { void retry() {} }\n");
        String headSha = commit("Add retry handling");
        registerMergedPullRequestTouching(217, "Add retry handling", "PaymentProcessor.java");
        selectPullRequest(baseSha, headSha);

        ContextRewindResponse response = controller.contextRewind("PaymentProcessor", null);

        assertThat(response.entityName()).isEqualTo("PaymentProcessor");
        assertThat(response.pullRequestReferences())
                .containsExactly(new PullRequestReferenceResponse(217, REPOSITORY, "https://github.com/" + REPOSITORY + "/pull/217"));
        assertThat(response.insufficientHistoryMessage()).isNull();
    }

    @Test
    void mapsInsufficientHistoryMessageWhenNoneIsAvailable() throws Exception {
        session.connect(TOKEN);
        initRepo();
        writeFile("README.md", "root\n");
        String baseSha = commit("Initial commit");
        writeFile("README.md", "root, updated\n");
        String headSha = commit("Second commit");
        selectPullRequest(baseSha, headSha);

        ContextRewindResponse response = controller.contextRewind("UntouchedHelper", null);

        assertThat(response.insufficientHistoryMessage())
                .isEqualTo("Not enough historical information is available for UntouchedHelper");
        assertThat(response.evolutionTimeline()).isEmpty();
    }

    @Test
    void includesAnAiNarrativeWhenAProviderIsConfigured() throws Exception {
        session.connect(TOKEN);
        initRepo();
        writeFile("PaymentProcessor.java", "class PaymentProcessor {}\n");
        String baseSha = commit("Initial commit");
        writeFile("PaymentProcessor.java", "class PaymentProcessor { void retry() {} }\n");
        String headSha = commit("Second commit");
        registerMergedPullRequestTouching(217, "Add retry handling", "PaymentProcessor.java");
        selectPullRequest(baseSha, headSha);
        ContextNarrativeProvider fixedNarrativeProvider = (entityName, facts) -> "PaymentProcessor grew retry handling over time.";
        ContextRewindController controllerWithNarrative =
                new ContextRewindController(session, token -> fakeRepositoryProvider, fixedNarrativeProvider);

        ContextRewindResponse response = controllerWithNarrative.contextRewind("PaymentProcessor", null);

        assertThat(response.aiNarrative()).isEqualTo("PaymentProcessor grew retry handling over time.");
    }

    /**
     * Doesn't assert the resulting {@code evolutionTimeline} content: {@link ContextRewindService}'s
     * own "catch me up" scoping is already covered directly against a full-history repo root in
     * {@code ContextRewindServiceTest#catchUpScopesActivityToAfterTheGivenPoint} (ticket #161).
     * Through this controller, {@code selection.headRoot()} is a shallow, single-commit checkout
     * (see the controller's class Javadoc), so a real multi-commit scoping assertion here would
     * either trivially pass regardless of whether {@code since} is even wired up, or require
     * bypassing the session's real checkout mechanics — neither is a meaningful test. This test
     * covers what the controller itself is actually responsible for: parsing {@code since} and
     * rejecting a malformed one, rather than silently ignoring it.
     */
    @Test
    void rejectsAMalformedSinceParameter() throws Exception {
        session.connect(TOKEN);
        initRepo();
        writeFile("README.md", "root\n");
        String baseSha = commit("Initial commit");
        writeFile("README.md", "root, updated\n");
        String headSha = commit("Second commit");
        selectPullRequest(baseSha, headSha);

        assertThatThrownBy(() -> controller.contextRewind("PaymentProcessor", "not-a-date"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    private void registerMergedPullRequestTouching(int number, String title, String changedFile) {
        transport.addClosedPullRequest(REPOSITORY, number, title, true);
        transport.addPullRequestDetail(REPOSITORY, number, title, "octocat", "main", "feature");
        transport.addChangedFile(REPOSITORY, number, changedFile, "modified", null);
    }

    private void selectPullRequest(String baseSha, String headSha) {
        ImportedPullRequest pr = new ImportedPullRequest(217, "Add retry handling", "octocat", baseSha, headSha,
                List.of(), List.of());
        Path baseRoot = GitRevisionCheckout.checkout(repoDir.toString(), baseSha, workDir, Map.of());
        Path headRoot = GitRevisionCheckout.checkout(repoDir.toString(), headSha, workDir, Map.of());
        session.select(new WebSession.SelectedPullRequest(
                pr, REPOSITORY, workDir, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }

    private void selectStandaloneDiff(String baseSha, String headSha) {
        Path baseRoot = GitRevisionCheckout.checkout(repoDir.toString(), baseSha, workDir, Map.of());
        Path headRoot = GitRevisionCheckout.checkout(repoDir.toString(), headSha, workDir, Map.of());
        session.selectDiff(new Diff(workDir, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }

    private void initRepo() throws IOException, InterruptedException {
        run(repoDir, "git", "init", "--quiet");
        run(repoDir, "git", "config", "user.email", "test@example.com");
        run(repoDir, "git", "config", "user.name", "Test");
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String commit(String message) throws IOException, InterruptedException {
        run(repoDir, "git", "add", ".");
        run(repoDir, "git", "commit", "--quiet", "-m", message);
        return runAndCapture(repoDir, "git", "rev-parse", "HEAD").strip();
    }

    private void run(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + stderr);
        }
    }

    private String runAndCapture(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(false).start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        return output;
    }
}
