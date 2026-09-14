package com.athena.cli;

import com.athena.git.GitAskpass;
import com.athena.git.TempDirectories;
import com.athena.github.GitHubTransport;
import com.athena.github.HttpGitHubTransport;
import com.athena.github.PullRequestImporter;
import com.athena.repository.ImportedPullRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * The CLI entry point, with two invocation modes distinguished by argument count:
 * <ul>
 *   <li>{@code java -cp ... com.athena.cli.Main <owner/repo> <pr-number>} (ticket #65) — a
 *       real PR, imported via a GitHub Personal Access Token in the {@code GITHUB_TOKEN}
 *       environment variable, never a command-line argument, so it can't leak into shell
 *       history or a process listing.</li>
 *   <li>{@code java -cp ... com.athena.cli.Main <repository-path> <base-revision> <head-revision>}
 *       (ticket #111/#154) — a standalone local Diff, no GitHub involved at all.</li>
 * </ul>
 * Thin by design: all the actual logic lives in {@link PrReviewSummary} and its
 * collaborators, which are tested directly; this class is just argument/environment
 * parsing and wiring.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 3) {
            runLocalDiff(args[0], args[1], args[2]);
            return;
        }
        if (args.length == 2) {
            runPrReview(args[0], args[1]);
            return;
        }
        System.err.println("Usage: <repository-full-name> <pr-number>");
        System.err.println("   or: <repository-path> <base-revision> <head-revision>");
        System.exit(1);
    }

    private static void runPrReview(String repositoryFullName, String prNumberArg) {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("The GITHUB_TOKEN environment variable is required");
            System.exit(1);
            return;
        }

        int prNumber = Integer.parseInt(prNumberArg);

        GitHubTransport transport = new HttpGitHubTransport(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        ImportedPullRequest pr = new PullRequestImporter(token, transport)
                .importPullRequest(repositoryFullName, prNumber);

        String repositoryUrl = "https://github.com/" + repositoryFullName + ".git";
        Map<String, String> gitEnvironment = GitAskpass.environmentFor(token);

        withWorkDir(workDir -> PrReviewSummary.buildFor(repositoryUrl, workDir, gitEnvironment, pr));
    }

    private static void runLocalDiff(String repositoryPath, String baseRevision, String headRevision) {
        withWorkDir(workDir -> PrReviewSummary.buildForLocalDiff(repositoryPath, workDir, baseRevision, headRevision));
    }

    private static void withWorkDir(Function<Path, String> summaryFor) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("athena-review-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a working directory", e);
        }
        try {
            System.out.println(summaryFor.apply(workDir));
        } finally {
            TempDirectories.deleteRecursively(workDir);
        }
    }
}
