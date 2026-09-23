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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code unrepresented_changed_files.feature} (ticket #260): the changed files no
 * Change represents, why, and the raw diff of any changed file — for a standalone Diff,
 * exercised through the controllers the web UI calls.
 */
public class UnrepresentedChangedFilesSteps {

    private static final String JAVA_CLASS = "public class %s {\n    public String name() {\n        return \"%s\";\n    }\n}\n";
    private static final String JAVA_CLASS_WITH_ADDED_METHOD =
            "public class %s {\n    public String name() {\n        return \"%s\";\n    }\n"
                    + "    public String label() {\n        return \"label\";\n    }\n}\n";

    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final DiffSelectionController diffController = new DiffSelectionController(session);
    private final RepresentationCoverageController coverageController = new RepresentationCoverageController(session);
    private GitRepositoryFixture repository;

    private String brokenFile;
    private UnrepresentedFilesResponse listing;
    private RawDiffResponse rawDiff;
    private ResponseStatusException rejection;

    @After
    public void deleteRepository() {
        if (repository != null) {
            repository.delete();
        }
    }

    @Given("the user has created a standalone Diff where file {string} changed but no Change was detected for it")
    public void a_diff_where_a_file_changed_without_a_change(String file) {
        String className = file.replace(".java", "");
        repository().write(file, JAVA_CLASS.formatted(className, className));
        String base = repository().commit("base");
        // An import-only edit: no declaration changes, so no detector produces a Change for it.
        repository().write(file, "import java.util.List;\n\n" + JAVA_CLASS.formatted(className, className));
        createDiff(base, repository().commit("head"));
    }

    @Given("the user has created a standalone Diff where only the build file {string} and one Java file changed")
    public void a_diff_where_a_build_file_and_a_java_file_changed(String buildFile) {
        repository().write(buildFile, "<project><version>1</version></project>\n");
        repository().write("Greeter.java", JAVA_CLASS.formatted("Greeter", "Greeter"));
        String base = repository().commit("base");
        repository().write(buildFile, "<project><version>2</version></project>\n");
        repository().write("Greeter.java", JAVA_CLASS_WITH_ADDED_METHOD.formatted("Greeter", "Greeter"));
        createDiff(base, repository().commit("head"));
    }

    @Given("a Change was detected for the Java file")
    public void a_change_was_detected_for_the_java_file() {
        assertThat(session.currentDiff().orElseThrow().changes()).isNotEmpty();
    }

    @Given("the user has created a standalone Diff where one changed Java file fails to parse")
    public void a_diff_where_a_changed_file_fails_to_parse() {
        brokenFile = "Broken.java";
        repository().write("Greeter.java", JAVA_CLASS.formatted("Greeter", "Greeter"));
        repository().write(brokenFile, JAVA_CLASS.formatted("Broken", "Broken"));
        String base = repository().commit("base");
        repository().write("Greeter.java", JAVA_CLASS_WITH_ADDED_METHOD.formatted("Greeter", "Greeter"));
        repository().write(brokenFile, "public class Broken {\n    public void m( { not java\n");
        createDiff(base, repository().commit("head"));
    }

    @Given("the user has created a standalone Diff where {int} files changed and Changes were detected for {int} of them")
    public void a_diff_where_some_changed_files_have_changes(int changed, int represented) {
        for (int i = 0; i < changed; i++) {
            repository().write("File" + i + ".java", JAVA_CLASS.formatted("File" + i, "file" + i));
        }
        String base = repository().commit("base");
        for (int i = 0; i < changed; i++) {
            String head = i < represented
                    ? JAVA_CLASS_WITH_ADDED_METHOD.formatted("File" + i, "file" + i)
                    : "import java.util.List;\n\n" + JAVA_CLASS.formatted("File" + i, "file" + i);
            repository().write("File" + i + ".java", head);
        }
        createDiff(base, repository().commit("head"));
    }

    @Given("the user has created a standalone Diff where file {string} is identical in both revisions")
    public void a_diff_where_a_file_is_identical(String file) {
        repository().write(file, JAVA_CLASS.formatted(file.replace(".java", ""), "same"));
        repository().write("Greeter.java", JAVA_CLASS.formatted("Greeter", "Greeter"));
        String base = repository().commit("base");
        repository().write("Greeter.java", JAVA_CLASS_WITH_ADDED_METHOD.formatted("Greeter", "Greeter"));
        createDiff(base, repository().commit("head"));
    }

    @Given("the user has created a standalone Diff where every changed file has at least one Change")
    public void a_diff_where_every_changed_file_has_a_change() {
        repository().write("Greeter.java", JAVA_CLASS.formatted("Greeter", "Greeter"));
        String base = repository().commit("base");
        repository().write("Greeter.java", JAVA_CLASS_WITH_ADDED_METHOD.formatted("Greeter", "Greeter"));
        createDiff(base, repository().commit("head"));
    }

    @Given("the user has not selected a PR or created a Diff")
    public void nothing_is_selected() {
        // no-op: a fresh WebSession has no selection
    }

    @When("the user requests the changed files not represented by any Change")
    public void the_user_requests_the_unrepresented_files() {
        try {
            listing = coverageController.unrepresentedFiles();
        } catch (ResponseStatusException e) {
            rejection = e;
        }
    }

    @When("the user requests the raw diff of {string}")
    public void the_user_requests_the_raw_diff_of(String file) {
        try {
            rawDiff = coverageController.rawDiff(file);
        } catch (ResponseStatusException e) {
            rejection = e;
        }
    }

    @Then("{string} is listed as unrepresented with reason {string}")
    public void is_listed_as_unrepresented_with_reason(String file, String reason) {
        assertThat(listing.files()).anySatisfy(entry -> {
            assertThat(entry.path()).isEqualTo(file);
            assertThat(entry.reasonLabel()).isEqualTo(reason);
        });
    }

    @Then("its listing shows whether it was added, modified or removed, and how many lines and hunks changed")
    public void its_listing_shows_status_lines_and_hunks() {
        assertThat(listing.files()).singleElement().satisfies(entry -> {
            assertThat(entry.status()).isEqualTo("MODIFIED");
            assertThat(entry.linesChanged()).isEqualTo(2);
            assertThat(entry.hunkCount()).isEqualTo(1);
        });
    }

    @Then("the Java file is not listed")
    public void the_java_file_is_not_listed() {
        assertThat(listing.files()).noneMatch(entry -> entry.path().endsWith(".java"));
    }

    @Then("that file is listed as unrepresented with reason {string}")
    public void that_file_is_listed_with_reason(String reason) {
        is_listed_as_unrepresented_with_reason(brokenFile, reason);
    }

    @Then("the listing reports {int} changed files, of which {int} are represented")
    public void the_listing_reports_counts(int changed, int represented) {
        assertThat(listing.changedFileCount()).isEqualTo(changed);
        assertThat(listing.representedFileCount()).isEqualTo(represented);
    }

    @Then("{string} is not listed")
    public void is_not_listed(String file) {
        assertThat(listing.files()).noneMatch(entry -> entry.path().equals(file));
    }

    @Then("no file is listed as unrepresented")
    public void no_file_is_listed() {
        assertThat(listing.files()).isEmpty();
    }

    @Then("the unified diff of {string} between the two revisions is returned")
    public void the_unified_diff_is_returned(String file) {
        assertThat(rawDiff.path()).isEqualTo(file);
        assertThat(rawDiff.diff().lines()).anyMatch(line -> line.equals("+import java.util.List;"));
        assertThat(rawDiff.diff().lines()).anyMatch(line -> line.startsWith(" public class"));
    }

    @Then("the request is rejected as not found")
    public void the_request_is_rejected_as_not_found() {
        assertThat(rejection.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Then("the request is rejected because nothing is selected")
    public void the_request_is_rejected_because_nothing_is_selected() {
        assertThat(rejection.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** Created on first use: Cucumber instantiates this class for every scenario, not just this feature's. */
    private GitRepositoryFixture repository() {
        if (repository == null) {
            repository = GitRepositoryFixture.create();
        }
        return repository;
    }

    private void createDiff(String base, String head) {
        diffController.createDiff(new DiffSelectionController.CreateDiffRequest(repository().directory().toString(), base, head));
    }
}
