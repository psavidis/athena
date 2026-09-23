package com.athena.web.reviewui;

import com.athena.git.GitRevisionCheckout;
import com.athena.git.TempDirectories;
import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.semantic.SemanticDimension;
import com.athena.web.WebSession;
import com.athena.web.diff.GitRepositoryFixture;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code test_code_changes.feature} (ticket #285): Changes in test sources are
 * marked as test code in the Change Map, Change detail and Canvas topology, and never
 * inferred as a new capability — exercised through the controllers the web UI calls.
 */
public class TestCodeChangesSteps {

    private static final String CLASS = "package com.acme;\n\npublic class %s {\n    public String name() {\n        return \"n\";\n    }\n}\n";
    private static final String CLASS_WITH_ADDED_METHOD = "package com.acme;\n\npublic class %s {\n"
            + "    public String name() {\n        return \"n\";\n    }\n"
            + "    public String %s() {\n        return \"added\";\n    }\n}\n";

    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final ChangeMapController changeMapController = new ChangeMapController(session);
    private final ChangeDetailController detailController = new ChangeDetailController(session, () -> "reviewer");
    private final ModuleTopologyController topologyController = new ModuleTopologyController(session);
    private final SemanticProfileController profileController = new SemanticProfileController(session);

    private GitRepositoryFixture repository;
    private Path workDir;
    private ChangeMapResponse changeMap;
    private ChangeDetailResponse detail;
    private ModuleTopologyResponse topology;
    private SemanticProfileResponse profile;

    @After
    public void cleanUp() {
        if (repository != null) {
            repository.delete();
        }
        if (workDir != null) {
            TempDirectories.deleteRecursively(workDir);
        }
    }

    @Given("the reviewer has selected a PR where a method was added to the class in {string}")
    public void a_pr_where_a_method_was_added_in(String file) throws IOException {
        String className = className(file);
        repository = GitRepositoryFixture.create();
        repository.write(file, CLASS.formatted(className));
        String base = repository.commit("base");
        repository.write(file, CLASS_WITH_ADDED_METHOD.formatted(className, "added"));
        select(base, repository.commit("head"));
    }

    @Given("the reviewer has selected a PR where one method was added in production code and one in test code of the same module")
    public void a_pr_with_a_production_and_a_test_change() throws IOException {
        repository = GitRepositoryFixture.create();
        repository.write("core/src/main/java/com/acme/Greeter.java", CLASS.formatted("Greeter"));
        repository.write("core/src/test/java/com/acme/GreeterTest.java", CLASS.formatted("GreeterTest"));
        String base = repository.commit("base");
        repository.write("core/src/main/java/com/acme/Greeter.java", CLASS_WITH_ADDED_METHOD.formatted("Greeter", "greet"));
        repository.write("core/src/test/java/com/acme/GreeterTest.java",
                CLASS_WITH_ADDED_METHOD.formatted("GreeterTest", "greetsByName"));
        select(base, repository.commit("head"));
    }

    @When("the reviewer views the Change Map of that PR")
    public void the_reviewer_opens_the_change_map() {
        changeMap = changeMapController.changeMap();
    }

    @When("the reviewer opens the detail of the Change for that method")
    public void the_reviewer_opens_the_detail() {
        detail = detailController.changeDetail(addedMethodEntry().changeKey());
    }

    @When("the reviewer opens the Semantic Canvas topology")
    public void the_reviewer_opens_the_topology() {
        topology = topologyController.topology();
    }

    @When("the reviewer opens the Semantic Profile of the Change for that method")
    public void the_reviewer_opens_the_semantic_profile() {
        profile = profileController.semanticProfile(addedMethodEntry().changeKey());
    }

    @Then("the Change for that method is marked as test code")
    public void the_change_is_marked_as_test_code() {
        assertThat(addedMethodEntry().testCode()).isTrue();
    }

    @Then("the Change for that method is not marked as test code")
    public void the_change_is_not_marked_as_test_code() {
        assertThat(addedMethodEntry().testCode()).isFalse();
    }

    @Then("the detail marks the Change as test code")
    public void the_detail_marks_the_change_as_test_code() {
        assertThat(detail.testCode()).isTrue();
    }

    @Then("the module's territory lists both Changes")
    public void the_territory_lists_both_changes() {
        assertThat(coreTerritory().changeKeys()).hasSize(2);
    }

    @Then("only the test-code Change is marked as test code")
    public void only_the_test_change_is_marked() {
        String testChangeKey = changeMapController.changeMap().changes().stream()
                .filter(entry -> entry.description().contains("greetsByName"))
                .findFirst().orElseThrow().changeKey();
        assertThat(coreTerritory().testChangeKeys()).containsExactly(testChangeKey);
    }

    @Then("the profile has no Responsibility classification")
    public void the_profile_has_no_responsibility_classification() {
        assertThat(profile.dimensions()).noneMatch(entry -> entry.dimension() == SemanticDimension.RESPONSIBILITY);
    }

    @Then("the profile still has a Structural classification")
    public void the_profile_still_has_a_structural_classification() {
        assertThat(profile.dimensions()).anyMatch(entry -> entry.dimension() == SemanticDimension.STRUCTURAL);
    }

    @Then("the profile's Responsibility classification is {string}")
    public void the_profiles_responsibility_classification_is(String conceptName) {
        assertThat(profile.dimensions())
                .filteredOn(entry -> entry.dimension() == SemanticDimension.RESPONSIBILITY)
                .extracting(SemanticDimensionEntryResponse::conceptName)
                .containsExactly(conceptName);
    }

    private ChangeEntryResponse addedMethodEntry() {
        List<ChangeEntryResponse> entries = (changeMap != null ? changeMap : changeMapController.changeMap()).changes();
        return entries.stream().filter(entry -> entry.description().contains("added")).findFirst().orElseThrow();
    }

    private ModuleTerritoryResponse coreTerritory() {
        return topology.territories().stream()
                .filter(territory -> territory.moduleName().equals("core"))
                .findFirst().orElseThrow();
    }

    private void select(String baseSha, String headSha) throws IOException {
        session.connect("test-token");
        workDir = Files.createTempDirectory("athena-test-code-work-");
        String repo = repository.directory().toString();
        ImportedPullRequest pr = new ImportedPullRequest(1, "Add a method", "author", baseSha, headSha, List.of(), List.of());
        Path baseRoot = GitRevisionCheckout.checkout(repo, baseSha, workDir, Map.of());
        Path headRoot = GitRevisionCheckout.checkout(repo, headSha, workDir, Map.of());
        session.select(new WebSession.SelectedPullRequest(pr, "acme/widgets", workDir, baseRoot, headRoot,
                new ReviewStateStore(), new AnnotationBoard(), new ReviewSubmission()));
    }

    private static String className(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        return name.substring(0, name.length() - ".java".length());
    }
}
