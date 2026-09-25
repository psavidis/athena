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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code test_code_changes.feature} (ticket #285): Changes in test sources are
 * marked as test code in the Change Map, Change detail and Canvas topology, and never
 * inferred as a new capability — exercised through the controllers the web UI calls. Also
 * the PR-/module-level grouping built on top of it: {@code test_change_grouping.feature}
 * (ticket #287) and {@code repeated_structural_change_grouping.feature} (ticket #291). One
 * glue class, since they share the selected-PR fixture and Cucumber here has no shared world.
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

    // --- Ticket #287: test changes grouped per test class ---

    private static final String MAIN = "core/src/main/java/com/acme/";
    private static final String TEST = "core/src/test/java/com/acme/";

    private SemanticDimensionEntryResponse lastGroup;

    @Given("the reviewer has selected a PR where {int} methods were added to test class {string} and {int} to production class {string}")
    public void a_pr_with_test_and_production_methods(int testMethods, String testClass, int productionMethods,
                                                      String productionClass) throws IOException {
        repository = GitRepositoryFixture.create();
        repository.write(TEST + testClass + ".java", classWithMethods(testClass, 0, ""));
        repository.write(MAIN + productionClass + ".java", classWithMethods(productionClass, 0, ""));
        String base = repository.commit("base");
        repository.write(TEST + testClass + ".java", classWithMethods(testClass, testMethods, ""));
        repository.write(MAIN + productionClass + ".java", classWithMethods(productionClass, productionMethods, ""));
        select(base, repository.commit("head"));
    }

    @Given("the reviewer has selected a PR where methods were added to test class {string} and to its nested class {string}")
    public void a_pr_with_methods_added_to_a_test_class_and_its_nested_class(String testClass, String nestedClass)
            throws IOException {
        repository = GitRepositoryFixture.create();
        String nestedBefore = "    static class " + nestedClass + " {\n    }\n";
        String nestedAfter = "    static class " + nestedClass + " {\n        void make() {\n        }\n    }\n";
        repository.write(TEST + testClass + ".java", classWithMethods(testClass, 0, nestedBefore));
        String base = repository.commit("base");
        repository.write(TEST + testClass + ".java", classWithMethods(testClass, 1, nestedAfter));
        select(base, repository.commit("head"));
    }

    @Given("the reviewer has selected a PR where methods were added to test classes {string} and {string}")
    public void a_pr_with_methods_added_to_two_test_classes(String first, String second) throws IOException {
        repository = GitRepositoryFixture.create();
        repository.write(TEST + first + ".java", classWithMethods(first, 0, ""));
        repository.write(TEST + second + ".java", classWithMethods(second, 0, ""));
        String base = repository.commit("base");
        repository.write(TEST + first + ".java", classWithMethods(first, 1, ""));
        // A differently named method: the same one in both would fold into one entry (ticket #363).
        repository.write(TEST + second + ".java", classWithMethods(second, 0, "    void verify() {\n    }\n"));
        select(base, repository.commit("head"));
    }

    @When("the reviewer opens the PR-level semantic profile")
    public void the_reviewer_opens_the_pr_level_semantic_profile() {
        profile = profileController.pullRequestSemanticProfile();
    }

    @When("the reviewer opens the semantic profile of module {string}")
    public void the_reviewer_opens_the_semantic_profile_of_module(String moduleName) {
        profile = profileController.moduleSemanticProfile(moduleName);
    }

    @Then("the Structural entries include one {string} entry folding {int} change(s)")
    public void the_structural_entries_include_a_group(String name, int count) {
        assertThat(structuralEntries()).filteredOn(entry -> entry.conceptName().equals(name))
                .singleElement()
                .satisfies(entry -> assertThat(entry.groupedMoveCount()).isEqualTo(count));
        lastGroup = structuralEntries().stream().filter(entry -> entry.conceptName().equals(name)).findFirst().orElseThrow();
    }

    @Then("that entry lists the file {string}")
    public void that_entry_lists_the_file(String file) {
        assertThat(lastGroup.filesTouched()).containsExactly(file);
    }

    @Then("no other Structural entry is a change in {string}")
    public void no_other_structural_entry_is_a_change_in(String testClass) {
        assertThat(structuralEntries()).filteredOn(entry -> entry != lastGroup)
                .noneMatch(entry -> entry.filesTouched().stream().anyMatch(file -> file.endsWith(testClass + ".java")));
    }

    @Then("every production Structural entry comes before every test group")
    public void production_entries_come_before_test_groups() {
        List<Boolean> isTestGroup = structuralEntries().stream()
                .map(entry -> entry.conceptName().startsWith("Test changes in ")).toList();
        assertThat(isTestGroup).contains(false, true);
        assertThat(isTestGroup.subList(isTestGroup.indexOf(true), isTestGroup.size())).containsOnly(true);
    }

    @Then("the Change Map lists {int} Changes")
    public void the_change_map_lists_n_changes(int count) {
        assertThat(changeMap.changes()).hasSize(count);
    }

    private List<SemanticDimensionEntryResponse> structuralEntries() {
        return profile.dimensions().stream().filter(entry -> entry.dimension() == SemanticDimension.STRUCTURAL).toList();
    }

    private static String classWithMethods(String className, int methods, String extraMembers) {
        StringBuilder body = new StringBuilder("package com.acme;\n\npublic class " + className + " {\n");
        for (int i = 0; i < methods; i++) {
            body.append("    void check").append(i).append("() {\n    }\n");
        }
        return body.append(extraMembers).append("}\n").toString();
    }

    // --- Ticket #291: the same structural change repeated across classes ---

    @Given("the reviewer has selected a PR where method {string} was removed from classes {string}, {string} and {string}")
    public void a_pr_where_a_method_was_removed_from_three_classes(String method, String a, String b, String c)
            throws IOException {
        repository = GitRepositoryFixture.create();
        for (String className : List.of(a, b, c)) {
            repository.write(MAIN + className + ".java", classDeclaring(className, List.of(method)));
        }
        String base = repository.commit("base");
        for (String className : List.of(a, b, c)) {
            repository.write(MAIN + className + ".java", classDeclaring(className, List.of()));
        }
        select(base, repository.commit("head"));
    }

    @Given("the reviewer has selected a PR where method {string} was removed from classes {string}, {string} and {string}, and method {string} from {string} only")
    public void a_pr_where_a_method_was_removed_from_three_classes_and_another_from_one(
            String method, String a, String b, String c, String other, String onlyClass) throws IOException {
        repository = GitRepositoryFixture.create();
        for (String className : List.of(a, b, c)) {
            List<String> setters = className.equals(onlyClass) ? List.of(method, other) : List.of(method);
            repository.write(MAIN + className + ".java", classDeclaring(className, setters));
        }
        String base = repository.commit("base");
        for (String className : List.of(a, b, c)) {
            repository.write(MAIN + className + ".java", classDeclaring(className, List.of()));
        }
        select(base, repository.commit("head"));
    }

    @Given("the reviewer has selected a PR where constructor parameter {string} was added to classes {string} and {string}")
    public void a_pr_where_a_constructor_parameter_was_added(String parameter, String a, String b) throws IOException {
        repository = GitRepositoryFixture.create();
        for (String className : List.of(a, b)) {
            repository.write(MAIN + className + ".java", "package com.acme;\n\npublic class " + className + " {\n"
                    + "    public " + className + "() {\n    }\n}\n");
        }
        String base = repository.commit("base");
        for (String className : List.of(a, b)) {
            repository.write(MAIN + className + ".java", "package com.acme;\n\npublic class " + className + " {\n"
                    + "    private final Object " + parameter + ";\n"
                    + "    public " + className + "(Object " + parameter + ") {\n        this." + parameter + " = "
                    + parameter + ";\n    }\n}\n");
        }
        select(base, repository.commit("head"));
    }

    @Given("the reviewer has selected a PR where method {string} was removed from class {string} and added to class {string}")
    public void a_pr_where_a_method_was_removed_from_one_class_and_added_to_another(String method, String from, String to)
            throws IOException {
        repository = GitRepositoryFixture.create();
        repository.write(MAIN + from + ".java", classDeclaring(from, List.of(method)));
        repository.write(MAIN + to + ".java", classDeclaring(to, List.of()));
        String base = repository.commit("base");
        repository.write(MAIN + from + ".java", classDeclaring(from, List.of()));
        // A different body, so this isn't matched as a move of the same method.
        repository.write(MAIN + to + ".java", "package com.acme;\n\npublic class " + to + " {\n"
                + "    public void " + method + "(Object value) {\n        System.out.println(value);\n    }\n}\n");
        select(base, repository.commit("head"));
    }

    @Given("the reviewer has selected a PR where classes {string}, {string} and {string} were each made final")
    public void a_pr_where_classes_were_made_final(String a, String b, String c) throws IOException {
        repository = GitRepositoryFixture.create();
        for (String className : List.of(a, b, c)) {
            repository.write(MAIN + className + ".java", "package com.acme;\n\npublic class " + className + " {\n}\n");
        }
        String base = repository.commit("base");
        for (String className : List.of(a, b, c)) {
            repository.write(MAIN + className + ".java", "package com.acme;\n\npublic final class " + className + " {\n}\n");
        }
        select(base, repository.commit("head"));
    }

    @Given("the reviewer has selected a PR where class {string} changed from {string} to {string}")
    public void a_pr_where_a_class_changed_its_supertypes(String className, String before, String after) throws IOException {
        repository = GitRepositoryFixture.create();
        repository.write(MAIN + className + ".java", classDeclaring(className, before));
        String base = repository.commit("base");
        repository.write(MAIN + className + ".java", classDeclaring(className, after));
        select(base, repository.commit("head"));
    }

    @Given("the reviewer has selected a PR where classes {string}, {string} and {string} each started implementing {string}")
    public void a_pr_where_classes_started_implementing(String a, String b, String c, String type) throws IOException {
        repository = GitRepositoryFixture.create();
        for (String className : List.of(a, b, c)) {
            repository.write(MAIN + className + ".java", classDeclaring(className, ""));
        }
        String base = repository.commit("base");
        for (String className : List.of(a, b, c)) {
            repository.write(MAIN + className + ".java", classDeclaring(className, "implements " + type));
        }
        select(base, repository.commit("head"));
    }

    @Then("the Change Map includes the Change {string}")
    public void the_change_map_includes_the_change(String description) {
        assertThat(changeMap.changes()).extracting(ChangeEntryResponse::description).contains(description);
    }

    @Then("no Change in the Change Map mentions {string}")
    public void no_change_mentions(String text) {
        assertThat(changeMap.changes()).extracting(ChangeEntryResponse::description).noneMatch(d -> d.contains(text));
    }

    private static String classDeclaring(String className, String supertypes) {
        return "package com.acme;\n\npublic class " + className + (supertypes.isEmpty() ? "" : " " + supertypes)
                + " {\n    public void close() {\n    }\n}\n";
    }

    @Then("that entry names the classes {string}")
    public void that_entry_names_the_classes(String classes) {
        assertThat(lastGroup.conceptDescription()).contains(classes);
    }

    @Then("no Structural entry mentions {string}")
    public void no_structural_entry_mentions(String text) {
        assertThat(structuralEntries()).extracting(SemanticDimensionEntryResponse::conceptName)
                .noneMatch(name -> name.contains(text));
    }

    private static String classDeclaring(String className, List<String> setters) {
        StringBuilder body = new StringBuilder("package com.acme;\n\npublic class " + className + " {\n");
        for (String setter : setters) {
            body.append("    public void ").append(setter).append("(Object value) {\n    }\n");
        }
        return body.append("}\n").toString();
    }

    // --- Ticket #363: the same change repeated across test classes ---

    @Given("the reviewer has selected a PR where method {string} was removed from test classes {string}, {string} and {string}")
    public void a_pr_where_a_method_was_removed_from_three_test_classes(String method, String a, String b, String c)
            throws IOException {
        Map<String, List<String>> removed = new LinkedHashMap<>();
        List.of(a, b, c).forEach(className -> removed.put(TEST + className, List.of(method)));
        removeFrom(removed);
    }

    @Given("the reviewer has selected a PR where method {string} was removed from test classes {string}, {string} and {string}, and method {string} from {string} only")
    public void a_pr_where_a_method_was_removed_from_three_test_classes_and_another_from_one(
            String method, String a, String b, String c, String other, String onlyClass) throws IOException {
        Map<String, List<String>> removed = new LinkedHashMap<>();
        for (String className : List.of(a, b, c)) {
            removed.put(TEST + className, className.equals(onlyClass) ? List.of(method, other) : List.of(method));
        }
        removeFrom(removed);
    }

    @Given("the reviewer has selected a PR where method {string} was removed from production class {string} and test class {string}")
    public void a_pr_where_a_method_was_removed_from_a_production_and_a_test_class(String method, String production,
                                                                                String test) throws IOException {
        Map<String, List<String>> removed = new LinkedHashMap<>();
        removed.put(MAIN + production, List.of(method));
        removed.put(TEST + test, List.of(method));
        removeFrom(removed);
    }

    /** Commits each class (a path prefix plus the class name) declaring its methods, then without them. */
    private void removeFrom(Map<String, List<String>> methodsByClass) throws IOException {
        repository = GitRepositoryFixture.create();
        methodsByClass.forEach((path, methods) -> repository.write(path + ".java", classDeclaring(className(path + ".java"), methods)));
        String base = repository.commit("base");
        methodsByClass.keySet().forEach(path -> repository.write(path + ".java", classDeclaring(className(path + ".java"), List.of())));
        select(base, repository.commit("head"));
    }
}
