package com.athena.web;

import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link WebSession}'s standalone-{@link Diff}
 * selection (ticket #111/#152) — {@link com.athena.web.diff.LocalDiffSteps}
 * already covers the end-to-end path through {@code DiffSelectionController};
 * this focuses on {@link WebSession} itself: the one-selection-at-a-time
 * mutual exclusion between a Diff and a PR Review.
 */
class WebSessionDiffTest {

    private final WebSession session = new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private Path diffWorkDir;
    private Path prWorkDir;

    @BeforeEach
    void createTempRoots() throws IOException {
        diffWorkDir = Files.createTempDirectory("athena-websession-diff-");
        prWorkDir = Files.createTempDirectory("athena-websession-pr-");
    }

    @AfterEach
    void cleanUpTempRoots() {
        deleteRecursively(diffWorkDir);
        deleteRecursively(prWorkDir);
    }

    @Test
    void aFreshSessionHasNoSelectedDiff() {
        assertThat(session.selectedDiff()).isEmpty();
    }

    @Test
    void selectingADiffMakesItRetrievable() {
        Diff diff = newDiff();

        session.selectDiff(diff);

        assertThat(session.selectedDiff()).contains(diff);
    }

    @Test
    void selectingADiffClearsAnySelectedPrReview() {
        session.select(newSelectedPullRequest());

        session.selectDiff(newDiff());

        assertThat(session.selectedPullRequest()).isEmpty();
    }

    @Test
    void selectingAPrReviewClearsAnySelectedDiff() {
        session.selectDiff(newDiff());

        session.select(newSelectedPullRequest());

        assertThat(session.selectedDiff()).isEmpty();
    }

    @Test
    void selectingASecondDiffReplacesTheFirst() {
        Diff first = newDiff();
        session.selectDiff(first);

        Diff second = newDiff();
        session.selectDiff(second);

        assertThat(session.selectedDiff()).contains(second);
    }

    @Test
    void clearingTheSelectedDiffLeavesNoSelectionWithoutRequiringAReplacement() {
        // DiffSelectionController's own use case: a Diff whose analysis fails right after
        // selectDiff has already set it must be removable from the session without going
        // through selectDiff again (which would try to delete the very directory the caller
        // is already in the middle of deleting itself).
        session.selectDiff(newDiff());

        session.clearSelectedDiff();

        assertThat(session.selectedDiff()).isEmpty();
    }

    private Diff newDiff() {
        return new Diff(diffWorkDir, diffWorkDir, diffWorkDir, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission());
    }

    private WebSession.SelectedPullRequest newSelectedPullRequest() {
        ImportedPullRequest pr = new ImportedPullRequest(1, "Some PR", "author", "base", "head", List.of(), List.of());
        return new WebSession.SelectedPullRequest(pr, "acme/widgets", prWorkDir, prWorkDir, prWorkDir,
                new ReviewStateStore(), new AnnotationBoard(), new ReviewSubmission());
    }

    private void deleteRecursively(Path root) {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of a temp directory
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup of a temp directory
        }
    }
}
