package com.athena.git;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for {@code git_checkout_resilience.feature} (ticket #359). */
public class GitCheckoutResilienceSteps {

    private FakeGitHost host;
    private String revision;
    private Path parentDir;
    private Optional<Path> checkedOut = Optional.empty();
    private Optional<GitCheckoutException> failure = Optional.empty();
    private Duration elapsed;

    @After
    public void cleanUp() {
        if (host != null) {
            host.delete();
        }
        checkedOut.ifPresent(TempDirectories::deleteRecursively);
        if (parentDir != null) {
            TempDirectories.deleteRecursively(parentDir);
        }
    }

    @Given("a git host whose fetch never finishes")
    public void a_stalling_host() {
        host = FakeGitHost.create(FakeGitHost.Behavior.STALL);
        revision = host.headRevision();
    }

    @Given("a git host that resets the connection on the first fetch")
    public void a_host_resetting_the_first_fetch() {
        host = FakeGitHost.create(FakeGitHost.Behavior.RESET_FIRST_FETCH);
        revision = host.headRevision();
    }

    @Given("a git host without the requested revision")
    public void a_host_without_the_revision() {
        host = FakeGitHost.create(FakeGitHost.Behavior.NORMAL);
        revision = "0123456789abcdef0123456789abcdef01234567";
    }

    @Given("a git host that answers normally")
    public void a_normal_host() {
        host = FakeGitHost.create(FakeGitHost.Behavior.NORMAL);
        revision = host.headRevision();
    }

    @When("Athena checks out a revision with a fetch time limit of {int} seconds")
    public void athena_checks_out_with_a_time_limit(int seconds) throws IOException {
        checkOut(FetchLimits.DEFAULT.withTimeout(Duration.ofSeconds(seconds)));
    }

    @When("Athena checks out a revision")
    public void athena_checks_out() throws IOException {
        checkOut(FetchLimits.DEFAULT);
    }

    @When("Athena checks out that revision")
    public void athena_checks_out_that_revision() throws IOException {
        checkOut(FetchLimits.DEFAULT);
    }

    @Then("the checkout fails within {int} seconds with an error saying the fetch timed out")
    public void the_checkout_times_out(int seconds) {
        assertThat(failure).hasValueSatisfying(e -> assertThat(e).hasMessageContaining("timed out"));
        assertThat(elapsed).isLessThan(Duration.ofSeconds(seconds));
    }

    @Then("the checkout succeeds")
    public void the_checkout_succeeds() {
        assertThat(failure).isEmpty();
        assertThat(checkedOut).hasValueSatisfying(dir -> assertThat(dir.resolve("README.md")).exists());
    }

    @Then("the checkout fails")
    public void the_checkout_fails() {
        assertThat(failure).isPresent();
    }

    @Then("the host saw {int} fetch(es)")
    public void the_host_saw_fetches(int count) {
        assertThat(host.fetches()).hasSize(count);
    }

    @Then("the fetch asked git to abort below {int} bytes per second for {int} seconds")
    public void the_fetch_asked_for_a_low_speed_limit(int bytesPerSecond, int seconds) {
        assertThat(host.fetches()).singleElement().satisfies(fetch -> assertThat(fetch)
                .contains("http.lowSpeedLimit=" + bytesPerSecond).contains("http.lowSpeedTime=" + seconds));
    }

    private void checkOut(FetchLimits limits) throws IOException {
        parentDir = Files.createTempDirectory("athena-checkout-parent-");
        long start = System.nanoTime();
        try {
            checkedOut = Optional.of(GitRevisionCheckout.checkout(host.url(), revision, parentDir, host.environment(), limits));
        } catch (GitCheckoutException e) {
            failure = Optional.of(e);
        }
        elapsed = Duration.ofNanos(System.nanoTime() - start);
    }
}
