package com.athena.web.diff;

import com.athena.git.GitRevisionCheckout;
import com.athena.git.TempDirectories;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.Change;
import com.athena.semantic.ReviewStateStore;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Creates a standalone {@link Diff} — comparing two revisions of a local
 * Git repository, with no GitHub App installation, token, or Pull Request
 * involved (ticket #111/#152). Reuses {@link GitRevisionCheckout} exactly
 * as {@code PullRequestSelectionController} does: it already accepts any
 * git-resolvable URL, a local filesystem path included, so this endpoint
 * needs no new git plumbing — only a caller that skips the GitHub import
 * step entirely.
 */
@RestController
public class DiffSelectionController {

    private final WebSession session;

    public DiffSelectionController(WebSession session) {
        this.session = session;
    }

    @PostMapping("/api/diffs")
    public DiffResponse createDiff(@RequestBody CreateDiffRequest request) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("athena-local-diff-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a working directory", e);
        }
        try {
            Path baseRoot = GitRevisionCheckout.checkout(request.repositoryPath(), request.baseRevision(), workDir, Map.of());
            Path headRoot = GitRevisionCheckout.checkout(request.repositoryPath(), request.headRevision(), workDir, Map.of());
            Diff diff = new Diff(workDir, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                    new ReviewSubmission());
            session.selectDiff(diff);
            // diff.changes() runs the actual analysis (PrAnalyzer.analyze, only wired up by
            // selectDiff above) — if THIS throws, the session already holds a Diff pointing at
            // workDir, which the catch block below is about to delete. Clear it back out first
            // so the session never keeps a reference to a directory that no longer exists.
            List<Change> changes = diff.changes();
            return new DiffResponse(changes.size());
        } catch (RuntimeException e) {
            if (session.selectedDiff().isPresent() && session.selectedDiff().get().workDir().equals(workDir)) {
                session.clearSelectedDiff();
            }
            TempDirectories.deleteRecursively(workDir);
            throw e;
        }
    }

    public record CreateDiffRequest(String repositoryPath, String baseRevision, String headRevision) {
    }

    public record DiffResponse(int changeCount) {
    }
}
