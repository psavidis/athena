package com.athena.cli;

import com.athena.git.GitAskpass;
import com.athena.git.TempDirectories;
import com.athena.github.GitHubTransport;
import com.athena.github.HttpGitHubTransport;
import com.athena.github.ImportedPullRequest;
import com.athena.github.PullRequestImporter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * The CLI entry point (ticket #65): {@code java -cp ... com.athena.cli.Main <owner/repo> <pr-number>},
 * with a GitHub Personal Access Token in the {@code GITHUB_TOKEN} environment variable — never a
 * command-line argument, so it can't leak into shell history or a process listing. Thin by
 * design: all the actual logic lives in {@link PrReviewSummary} and its collaborators, which are
 * tested directly; this class is just argument/environment parsing and wiring.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: <repository-full-name> <pr-number>");
            System.exit(1);
            return;
        }
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("The GITHUB_TOKEN environment variable is required");
            System.exit(1);
            return;
        }

        String repositoryFullName = args[0];
        int prNumber = Integer.parseInt(args[1]);

        GitHubTransport transport = new HttpGitHubTransport(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        ImportedPullRequest pr = new PullRequestImporter(token, transport)
                .importPullRequest(repositoryFullName, prNumber);

        String repositoryUrl = "https://github.com/" + repositoryFullName + ".git";
        Map<String, String> gitEnvironment = GitAskpass.environmentFor(token);

        Path workDir;
        try {
            workDir = Files.createTempDirectory("athena-review-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a working directory", e);
        }
        try {
            System.out.println(PrReviewSummary.buildFor(repositoryUrl, workDir, gitEnvironment, pr));
        } finally {
            TempDirectories.deleteRecursively(workDir);
        }
    }
}
