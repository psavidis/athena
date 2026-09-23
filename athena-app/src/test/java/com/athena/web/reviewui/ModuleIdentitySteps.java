package com.athena.web.reviewui;

import com.athena.plugins.PluginRegistry;
import com.athena.semantic.PrAnalyzer;
import com.athena.web.WebSession;
import com.athena.web.diff.DiffSelectionController;
import com.athena.web.diff.GitRepositoryFixture;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code module_identity_from_build_descriptors.feature} (ticket #292): territories
 * are the nearest build-descriptor directory above each changed file, for a standalone Diff,
 * exercised through the controllers the web UI calls.
 */
public class ModuleIdentitySteps {

    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final DiffSelectionController diffController = new DiffSelectionController(session);
    private final ModuleTopologyController topologyController = new ModuleTopologyController(session);
    private final SemanticProfileController profileController = new SemanticProfileController(session);

    private GitRepositoryFixture repository;
    private ModuleTopologyResponse topology;
    private SemanticProfileResponse moduleProfile;

    @After
    public void deleteRepository() {
        if (repository != null) {
            repository.delete();
        }
    }

    @Given("the user has created a standalone Diff changing classes in Gradle modules {string} and {string}")
    public void a_diff_changing_two_gradle_modules(String first, String second) {
        repository = GitRepositoryFixture.create();
        repository.write("settings.gradle", "rootProject.name = 'platform'\n");
        for (String module : List.of(first, second)) {
            repository.write(module + "/build.gradle", "plugins { id 'java' }\n");
        }
        // Distinct class names: two identical "Service#added" additions would be one Change.
        changeClasses(List.of(first + "/src/main/java/com/acme/FirstService.java",
                second + "/src/main/java/com/acme/SecondService.java"));
    }

    @Given("the user has created a standalone Diff changing a class under {string} of a Maven project with artifactId {string}")
    public void a_diff_in_a_maven_project(String sourceRoot, String artifactId) {
        repository = GitRepositoryFixture.create();
        repository.write("pom.xml", "<project>\n  <groupId>org.acme</groupId>\n  <artifactId>" + artifactId
                + "</artifactId>\n</project>\n");
        changeClasses(List.of(sourceRoot + "/com/acme/Service.java"));
    }

    @Given("the user has created a standalone Diff changing a class under {string} of a Maven project with artifactId {string} and parent artifactId {string}")
    public void a_diff_in_a_maven_project_with_a_parent(String sourceRoot, String artifactId, String parentArtifactId) {
        repository = GitRepositoryFixture.create();
        repository.write("pom.xml", "<project>\n  <parent>\n    <groupId>org.acme</groupId>\n    <artifactId>"
                + parentArtifactId + "</artifactId>\n  </parent>\n  <artifactId>" + artifactId + "</artifactId>\n</project>\n");
        changeClasses(List.of(sourceRoot + "/com/acme/Service.java"));
    }

    @Given("the user has created a standalone Diff changing a class under {string} of a Gradle project named {string}")
    public void a_diff_in_a_named_gradle_project(String sourceRoot, String name) {
        repository = GitRepositoryFixture.create();
        repository.write("settings.gradle.kts", "rootProject.name = \"" + name + "\"\n");
        repository.write("build.gradle.kts", "plugins { java }\n");
        changeClasses(List.of(sourceRoot + "/com/acme/Service.java"));
    }

    @Given("the user has created a standalone Diff changing a class under {string} of a Gradle project with no name")
    public void a_diff_in_an_unnamed_gradle_project(String sourceRoot) {
        repository = GitRepositoryFixture.create();
        repository.write("build.gradle", "plugins { id 'java' }\n");
        changeClasses(List.of(sourceRoot + "/com/acme/Service.java"));
    }

    @Given("the user has created a standalone Diff changing a production class and a test class of Gradle module {string}")
    public void a_diff_changing_production_and_test_classes(String module) {
        repository = GitRepositoryFixture.create();
        repository.write(module + "/build.gradle", "plugins { id 'java' }\n");
        changeClasses(List.of(module + "/src/main/java/com/acme/Service.java",
                module + "/src/test/java/com/acme/ServiceTest.java"));
    }

    @Given("the user has created a standalone Diff changing a class under {string} with no build descriptor anywhere")
    public void a_diff_without_build_descriptors(String sourceRoot) {
        repository = GitRepositoryFixture.create();
        changeClasses(List.of(sourceRoot + "/com/acme/Service.java"));
    }

    @When("the user requests the Semantic Canvas topology")
    public void the_user_requests_the_topology() {
        topology = topologyController.topology();
    }

    @When("the user requests the semantic profile of module {string}")
    public void the_user_requests_the_module_profile(String moduleName) {
        moduleProfile = profileController.moduleSemanticProfile(moduleName);
    }

    @Then("the territories are {string}")
    public void the_territories_are(String name) {
        assertThat(topology.territories()).extracting(ModuleTerritoryResponse::moduleName).containsExactly(name);
    }

    @Then("the territories are {string} and {string}")
    public void the_territories_are(String first, String second) {
        assertThat(topology.territories()).extracting(ModuleTerritoryResponse::moduleName)
                .containsExactlyInAnyOrder(first, second);
    }

    @Then("the module's semantic profile has at least one entry")
    public void the_module_profile_has_an_entry() {
        assertThat(moduleProfile.dimensions()).isNotEmpty();
    }

    /** Commits each file as an empty class, then adds a method to each: one Change per file. */
    private void changeClasses(List<String> files) {
        for (String file : files) {
            repository.write(file, classSource(file, ""));
        }
        String base = repository.commit("base");
        for (String file : files) {
            repository.write(file, classSource(file, "    public void added() {\n    }\n"));
        }
        diffController.createDiff(new DiffSelectionController.CreateDiffRequest(
                repository.directory().toString(), base, repository.commit("head")));
    }

    private static String classSource(String file, String body) {
        String className = file.substring(file.lastIndexOf('/') + 1).replace(".java", "");
        return "package com.acme;\n\npublic class " + className + " {\n" + body + "}\n";
    }
}
