package com.athena.git;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fetch limits, retries and failure classification of {@link GitRevisionCheckout} (ticket #359). */
class GitRevisionCheckoutTest {

    @TempDir
    Path parentDir;

    private FakeGitHost host;

    @AfterEach
    void deleteHost() {
        if (host != null) {
            host.delete();
        }
    }

    @Test
    void theDefaultLimitsAbortBelowOneKilobytePerSecondForAMinuteAndRetryOnce() {
        assertThat(FetchLimits.DEFAULT.lowSpeedBytesPerSecond()).isEqualTo(1000);
        assertThat(FetchLimits.DEFAULT.lowSpeedTime()).isEqualTo(Duration.ofSeconds(60));
        assertThat(FetchLimits.DEFAULT.retries()).isEqualTo(1);
    }

    @Test
    void aStalledFetchIsAbortedAfterTheTimeoutAndRetriedOnce() {
        host = FakeGitHost.create(FakeGitHost.Behavior.STALL);
        String revision = host.headRevision();

        assertThatThrownBy(() -> GitRevisionCheckout.checkout(host.url(), revision, parentDir, host.environment(),
                FetchLimits.DEFAULT.withTimeout(Duration.ofSeconds(1))))
                .isInstanceOf(GitCheckoutException.class).hasMessageContaining("timed out");
        assertThat(host.fetches()).hasSize(2);
    }

    @Test
    void aResetConnectionIsRetriedAndTheCheckoutSucceeds() {
        host = FakeGitHost.create(FakeGitHost.Behavior.RESET_FIRST_FETCH);

        Path dir = GitRevisionCheckout.checkout(host.url(), host.headRevision(), parentDir, host.environment());

        assertThat(dir.resolve("README.md")).exists();
        assertThat(host.fetches()).hasSize(2);
    }

    @Test
    void aMissingRevisionIsNotRetried() {
        host = FakeGitHost.create(FakeGitHost.Behavior.NORMAL);

        assertThatThrownBy(() -> GitRevisionCheckout.checkout(host.url(), "0123456789abcdef0123456789abcdef01234567",
                parentDir, host.environment())).isInstanceOf(GitCheckoutException.class);
        assertThat(host.fetches()).hasSize(1);
    }

    @Test
    void noRetriesMeansASingleAttempt() {
        host = FakeGitHost.create(FakeGitHost.Behavior.RESET_FIRST_FETCH);
        String revision = host.headRevision();

        assertThatThrownBy(() -> GitRevisionCheckout.checkout(host.url(), revision, parentDir, host.environment(),
                FetchLimits.DEFAULT.withRetries(0))).isInstanceOf(GitCheckoutException.class);
        assertThat(host.fetches()).hasSize(1);
    }

    @Test
    void transientNetworkErrorsAreRecognized() {
        assertThat(GitRevisionCheckout.isTransient("error: RPC failed; curl 56 Recv failure: Connection reset by peer")).isTrue();
        assertThat(GitRevisionCheckout.isTransient("fatal: unable to access '…': Operation too slow")).isTrue();
        assertThat(GitRevisionCheckout.isTransient("fatal: the remote end hung up unexpectedly")).isTrue();
        assertThat(GitRevisionCheckout.isTransient("fatal: couldn't find remote ref 0123")).isFalse();
        assertThat(GitRevisionCheckout.isTransient("fatal: Authentication failed for '…'")).isFalse();
    }
}
