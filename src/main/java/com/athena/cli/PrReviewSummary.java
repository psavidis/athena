package com.athena.cli;

import com.athena.github.ImportedPullRequest;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationDetector;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Proves the pipeline works end to end against a real PR (ticket #65):
 * checks out its base/head revisions, runs the existing semantic engine
 * against them unmodified, and formats a plain-text summary. Read-only —
 * no review state, no GitHub sync, no AI analysis.
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
        Path baseRoot = GitRevisionCheckout.checkout(repositoryUrl, pr.baseRevision(), workDir, gitEnvironment);
        Path headRoot = GitRevisionCheckout.checkout(repositoryUrl, pr.headRevision(), workDir, gitEnvironment);
        try {
            List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
            List<Change> changes = new ChangeGrouper().group(transformations);
            return format(pr.title(), changes);
        } finally {
            TempDirectories.deleteRecursively(baseRoot);
            TempDirectories.deleteRecursively(headRoot);
        }
    }

    private static String format(String prTitle, List<Change> changes) {
        StringBuilder text = new StringBuilder();
        text.append(prTitle).append(System.lineSeparator()).append(System.lineSeparator());
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
