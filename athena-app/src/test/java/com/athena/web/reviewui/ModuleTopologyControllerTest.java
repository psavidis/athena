package com.athena.web.reviewui;

import com.athena.git.GitRevisionCheckout;
import com.athena.git.TempDirectories;
import com.athena.repository.ImportedPullRequest;
import com.athena.plugins.PluginRegistry;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.ModuleStatus;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.semantic.TechStack;
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
 * Dedicated unit test for {@link ModuleTopologyController} (ticket #129) —
 * {@link com.athena.semantic.ModuleTopologyBuilder} already has its own
 * focused unit tests for the manifest-reading logic itself; this covers the
 * controller's own job: resolving the session's real checkouts, delegating,
 * and mapping to the response DTOs (including a real `ChangeKey` encoding).
 */
class ModuleTopologyControllerTest {

    private static final String REPOSITORY_FULL_NAME = "acme/widgets";

    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final ModuleTopologyController controller = new ModuleTopologyController(session);

    private Path repoDir;
    private Path workDir;

    @BeforeEach
    void createTempRoots() throws IOException {
        repoDir = Files.createTempDirectory("athena-topology-repo-");
        workDir = Files.createTempDirectory("athena-topology-work-");
    }

    @AfterEach
    void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(repoDir);
        TempDirectories.deleteRecursively(workDir);
    }

    @Test
    void rejectsWhenNoPullRequestIsSelected() {
        session.connect("test-token");

        assertThatThrownBy(controller::topology)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    /**
     * Ticket #111/#153: a standalone {@link com.athena.web.Diff} (no GitHub Pull
     * Request, no GitHub connection at all) must render the same territory map a
     * selected PR Review does.
     */
    @Test
    void rendersTheTerritoryMapForAStandaloneDiffWithNoGitHubConnection() throws Exception {
        initRepo();
        writeFile("README.md", "root\n");
        String baseSha = commit("Initial commit");

        Files.createDirectories(repoDir.resolve("crowdness-live"));
        writeFile("crowdness-live/Live.java", "public class Live {\n"
                + "    public void start() {\n"
                + "    }\n"
                + "}\n");
        String headSha = commit("Add crowdness-live module");

        selectStandaloneDiff(baseSha, headSha);

        ModuleTopologyResponse response = controller.topology();

        assertThat(response.territories()).isNotEmpty();
    }

    @Test
    void reportsANewSpringBootModuleWithARealChangeKey() throws Exception {
        session.connect("test-token");
        initRepo();
        writeFile("README.md", "root\n");
        String baseSha = commit("Initial commit");

        Files.createDirectories(repoDir.resolve("crowdness-live"));
        writeFile("crowdness-live/pom.xml", "<project>\n"
                + "  <artifactId>crowdness-live</artifactId>\n"
                + "  <dependencies>\n"
                + "    <dependency><artifactId>spring-boot-starter-web</artifactId></dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n");
        writeFile("crowdness-live/src/main/java/Live.java", "public class Live {\n"
                + "    public void start() {\n"
                + "    }\n"
                + "}\n");
        String headSha = commit("Add crowdness-live module");

        selectPullRequest(baseSha, headSha);

        ModuleTopologyResponse response = controller.topology();

        ModuleTerritoryResponse territory = response.territories().stream()
                .filter(t -> t.moduleName().equals("crowdness-live"))
                .findFirst()
                .orElseThrow();
        assertThat(territory.status()).isEqualTo(ModuleStatus.NEW);
        assertThat(territory.techStack()).isEqualTo(TechStack.SPRING_BOOT_JAVA);
        assertThat(territory.techStackLabel()).isEqualTo("Spring Boot · Java");
        assertThat(territory.changeKeys()).isNotEmpty();
    }

    private void selectPullRequest(String baseSha, String headSha) {
        ImportedPullRequest pr = new ImportedPullRequest(1, "Add live module", "author", baseSha, headSha,
                List.of(), List.of());
        Path baseRoot = GitRevisionCheckout.checkout(repoDir.toString(), baseSha, workDir, Map.of());
        Path headRoot = GitRevisionCheckout.checkout(repoDir.toString(), headSha, workDir, Map.of());
        session.select(new WebSession.SelectedPullRequest(
                pr, REPOSITORY_FULL_NAME, workDir, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
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
