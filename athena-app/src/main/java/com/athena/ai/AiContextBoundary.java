package com.athena.ai;

import com.athena.reviewcontext.ReviewContext;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.Change;
import com.athena.semantic.ReviewState;
import com.athena.semantic.ReviewStateStore;

import java.util.List;

/**
 * The context boundary a reviewer can inspect before triggering AI analysis
 * (epic #7 §36): exactly what will be included in, and excluded from, the
 * request sent to an {@link AiProvider}. {@link #payload()} is the single
 * source of truth for that request — it is the {@link ReviewContext} that
 * must be passed to {@link AiProvider#analyze(ReviewContext)}, so the
 * inspector's view and the actual request can never drift apart.
 *
 * <p>Three exclusions are enforced at assembly, not just hidden in a view:
 * private notes (this always assembles via {@link ReviewContext#assemble},
 * never {@code assembleIncludingPrivateNotes}), Changes the reviewer hasn't
 * acted on yet ({@link ReviewState#UNSEEN}/{@link ReviewState#UNDERSTANDING}),
 * and Changes touching only generated files (a {@code Skipped} Change is a
 * deliberate reviewer decision, not "unreviewed," and remains sendable).
 */
public final class AiContextBoundary {

    private final ReviewContext payload;
    private final List<Change> excludedUnreviewedChanges;
    private final List<Change> excludedGeneratedChanges;

    private AiContextBoundary(ReviewContext payload, List<Change> excludedUnreviewedChanges,
                               List<Change> excludedGeneratedChanges) {
        this.payload = payload;
        this.excludedUnreviewedChanges = List.copyOf(excludedUnreviewedChanges);
        this.excludedGeneratedChanges = List.copyOf(excludedGeneratedChanges);
    }

    public static AiContextBoundary assemble(String prTitle, List<Change> changes, ReviewStateStore store,
                                              AnnotationBoard board) {
        List<Change> generated = changes.stream().filter(AiContextBoundary::touchesOnlyGeneratedFiles).toList();
        List<Change> unreviewed = changes.stream()
                .filter(change -> !generated.contains(change))
                .filter(change -> isUnreviewed(store, change))
                .toList();
        List<Change> sendable = changes.stream()
                .filter(change -> !generated.contains(change) && !unreviewed.contains(change))
                .toList();

        ReviewContext payload = ReviewContext.assemble(prTitle, sendable, store, board);
        return new AiContextBoundary(payload, unreviewed, generated);
    }

    /** The exact {@link ReviewContext} to pass to {@link AiProvider#analyze(ReviewContext)}. */
    public ReviewContext payload() {
        return payload;
    }

    /** Always true: this boundary never assembles a payload that includes private notes. */
    public boolean privateNotesExcluded() {
        return true;
    }

    /** Changes still Unseen/Understanding — not yet acted on, so excluded from {@link #payload()}. */
    public List<Change> excludedUnreviewedChanges() {
        return excludedUnreviewedChanges;
    }

    /** Changes touching only generated files, excluded from {@link #payload()} regardless of review state. */
    public List<Change> excludedGeneratedChanges() {
        return excludedGeneratedChanges;
    }

    private static boolean isUnreviewed(ReviewStateStore store, Change change) {
        if (store.isMechanical(change)) {
            // Mechanical is a classification independent of review state (ReviewStateStore's own
            // distinction) — a Change can be marked mechanical while its state is still Unseen,
            // and that alone makes it accounted-for, not "unreviewed."
            return false;
        }
        ReviewState state = store.stateOf(change);
        return state == ReviewState.UNSEEN || state == ReviewState.UNDERSTANDING;
    }

    private static boolean touchesOnlyGeneratedFiles(Change change) {
        List<String> files = change.matchedOccurrences().stream()
                .flatMap(occurrence -> occurrence.filesTouched().stream())
                .toList();
        return !files.isEmpty() && files.stream().allMatch(GeneratedFilePaths::isGenerated);
    }
}
