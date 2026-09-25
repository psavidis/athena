package com.athena.web.reviewui;

import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.web.Diff;
import com.athena.semantic.PrAnalyzer;
import com.athena.web.WebSession;
import com.athena.web.diff.DiffSelectionController;
import com.athena.web.diff.GitRepositoryFixture;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code refactoring_recognition_end_to_end.feature} (tickets #384–#387): a real git
 * repository of plain classes, one refactoring applied as a second commit, and the Change Map
 * and Explorer the web UI shows for the two commits, through its controllers.
 */
public class RefactoringRecognitionSteps {

    private static final String PACKAGE_DIR = "src/main/java/com/acme/";

    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final DiffSelectionController diffController = new DiffSelectionController(session);
    private final ChangeMapController changeMapController = new ChangeMapController(session);
    private final SemanticProfileController profileController = new SemanticProfileController(session);

    private GitRepositoryFixture repository;
    private String baseCommit;
    private ChangeMapResponse changeMap;

    @After
    public void deleteRepository() {
        if (repository != null) {
            repository.delete();
        }
    }

    @Given("a repository whose classes {string}, {string} and {string} use each other")
    public void a_repository_of_three_classes(String account, String bank, String audit) {
        repository = GitRepositoryFixture.create();
        repository.write(PACKAGE_DIR + account + ".java", """
                package com.acme;

                public class Account {
                    private long balance;

                    public long getBalance() {
                        return balance;
                    }

                    public void deposit(long amount) {
                        long updated = balance + amount;
                        balance = updated;
                    }

                    public boolean canWithdraw(long amount) {
                        return balance >= amount;
                    }
                }
                """);
        repository.write(PACKAGE_DIR + bank + ".java", """
                package com.acme;

                public class Bank {
                    public long total(Account a, Account b) {
                        return a.getBalance() + b.getBalance();
                    }

                    public void pay(Account from, long amount) {
                        if (from.canWithdraw(amount)) {
                            from.deposit(-amount);
                        }
                    }
                }
                """);
        repository.write(PACKAGE_DIR + audit + ".java", """
                package com.acme;

                public class Audit {
                    public String describe(Account account) {
                        return "balance=" + account.getBalance();
                    }
                }
                """);
        baseCommit = repository.commit("base");
    }

    @When("the developer renames {string} to {string} in {string}")
    public void the_developer_renames_in_one_class(String from, String to, String className) {
        rename(from, to, List.of(className));
    }

    @When("the developer renames {string} to {string} in {string}, {string} and {string}")
    public void the_developer_renames_in_three_classes(String from, String to, String first, String second,
                                                      String third) {
        rename(from, to, List.of(first, second, third));
    }

    @When("the developer renames class {string} to {string}")
    public void the_developer_renames_a_class(String from, String to) {
        Path oldFile = repository.directory().resolve(PACKAGE_DIR + from + ".java");
        String source = read(oldFile);
        try {
            Files.delete(oldFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        repository.write(PACKAGE_DIR + to + ".java", source);
        try (var files = Files.list(repository.directory().resolve(PACKAGE_DIR))) {
            rename(from, to, files.map(file -> file.getFileName().toString().replace(".java", "")).toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @When("the reviewer opens the Change Map of the two commits")
    public void the_reviewer_opens_the_change_map() {
        String headCommit = repository.commit("refactor");
        diffController.createDiff(new DiffSelectionController.CreateDiffRequest(
                repository.directory().toString(), baseCommit, headCommit));
        // The Change Map serves a selected PR: re-home the analyzed Diff under one, as the
        // corpus snapshot tool does. No GitHub call is made.
        Diff diff = session.selectedDiff().orElseThrow();
        session.clearSelectedDiff();
        session.connect("placeholder-never-sent");
        session.select(new WebSession.SelectedPullRequest(
                new ImportedPullRequest(0, "refactor", "", baseCommit, headCommit, List.of(), List.of()), "local", diff));
        changeMap = changeMapController.changeMap();
    }

    @Then("the Change Map lists exactly {string}")
    public void the_change_map_lists_exactly(String description) {
        assertThat(changeMap.changes()).extracting(ChangeEntryResponse::description).containsExactly(description);
    }

    @Then("the Explorer shows no {string} entry")
    public void the_explorer_shows_no_entry(String conceptName) {
        assertThat(profileController.pullRequestSemanticProfile().dimensions())
                .extracting(SemanticDimensionEntryResponse::conceptName)
                .doesNotContain(conceptName);
    }

    /** Replaces the whole identifier {@code from} with {@code to} in each named class's file. */
    private void rename(String from, String to, List<String> classNames) {
        Pattern identifier = Pattern.compile("\\b" + Pattern.quote(from) + "\\b");
        for (String className : classNames) {
            Path file = repository.directory().resolve(PACKAGE_DIR + className + ".java");
            repository.write(PACKAGE_DIR + className + ".java",
                    identifier.matcher(read(file)).replaceAll(Matcher.quoteReplacement(to)));
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
