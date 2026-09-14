package com.athena.cli;

import com.athena.git.GitAskpass;
import com.athena.git.GitRevisionCheckout;
import com.athena.git.TempDirectories;
import com.athena.repository.ImportedPullRequest;
import com.athena.plugins.PluginRegistry;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.PrAnalyzer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Proves the pipeline works end to end against a real PR (ticket #65):
 * checks out its base/head revisions, runs the semantic engine — via
 * whichever {@link com.athena.semantic.spi.LanguagePlugin}/{@link
 * com.athena.semantic.spi.FrameworkPlugin} instances {@link PluginRegistry}
 * discovers on the classpath — against them, and formats a plain-text
 * summary. Read-only — no review state, no GitHub sync, no AI analysis.
 * {@link #buildForLocalDiff} (ticket #111/#154) reuses the exact same
 * checkout/analyze/format pipeline for two revisions of a local Git
 * repository — no {@link ImportedPullRequest}, no GitHub token.
 *
 * <p>Builds its own {@link PrAnalyzer} per call rather than taking one as a
 * parameter: unlike the web app, the CLI has no Spring context to manage a
 * shared instance, and {@link PluginRegistry}'s {@link
 * java.util.ServiceLoader} discovery is cheap enough not to need caching
 * across the single PR this process reviews.
 */
public final class PrReviewSummary {

    private PrReviewSummary() {
    }

    /**
     * @param repositoryUrl a git remote git can clone (a real GitHub HTTPS URL in production, a
     *                       local repository path in tests)
     * @param workDir        scratch directory for the base/head checkouts; both are deleted from
     *                       it again before this method returns, whether it succeeds or throws
     * @param gitEnvironment extra environment variables for the `git` subprocess (e.g. a
     *                       {@link GitAskpass} credential helper); empty for an unauthenticated
     *                       remote such as a local path
     */
    public static String buildFor(String repositoryUrl, Path workDir, Map<String, String> gitEnvironment,
                                   ImportedPullRequest pr) {
        return build(repositoryUrl, pr.baseRevision(), pr.headRevision(), workDir, gitEnvironment, pr.title());
    }

    /**
     * Compares two revisions of a local Git repository directly (ticket #111/#154) — no
     * {@link ImportedPullRequest}, no GitHub token, no {@code gitEnvironment} credential
     * helper, since {@link GitRevisionCheckout#checkout} already accepts a local filesystem
     * path unauthenticated. Reuses {@link #format} unmodified: a local Diff has no PR title,
     * so a plain label stands in for it.
     */
    public static String buildForLocalDiff(String repositoryPath, Path workDir, String baseRevision, String headRevision) {
        return build(repositoryPath, baseRevision, headRevision, workDir, Map.of(), "Local Diff");
    }

    private static String build(String repositoryUrl, String baseRevision, String headRevision, Path workDir,
                                 Map<String, String> gitEnvironment, String label) {
        Path baseRoot = null;
        Path headRoot = null;
        try {
            baseRoot = GitRevisionCheckout.checkout(repositoryUrl, baseRevision, workDir, gitEnvironment);
            headRoot = GitRevisionCheckout.checkout(repositoryUrl, headRevision, workDir, gitEnvironment);
            PrAnalyzer prAnalyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());
            List<Change> changes = prAnalyzer.analyze(baseRoot, headRoot).changes();
            return format(label, changes);
        } finally {
            if (baseRoot != null) {
                TempDirectories.deleteRecursively(baseRoot);
            }
            if (headRoot != null) {
                TempDirectories.deleteRecursively(headRoot);
            }
        }
    }

    private static String format(String label, List<Change> changes) {
        StringBuilder text = new StringBuilder();
        text.append(label).append(System.lineSeparator()).append(System.lineSeparator());
        for (ChangeCategory category : ChangeCategory.values()) {
            List<Change> inCategory = changes.stream().filter(change -> ChangeCategory.of(change.kind()) == category).toList();
            if (inCategory.isEmpty()) {
                continue;
            }
            text.append(category).append(":").append(System.lineSeparator());
            for (Change change : inCategory) {
                text.append("  - ").append(change).append(System.lineSeparator());
            }
        }
        return text.toString();
    }
}
