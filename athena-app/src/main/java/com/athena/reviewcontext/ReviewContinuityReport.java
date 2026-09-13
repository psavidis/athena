package com.athena.reviewcontext;

import com.athena.semantic.Change;
import com.athena.semantic.ReviewState;
import com.athena.semantic.ReviewStateStore;

import java.util.List;

/**
 * The result of comparing a prior revision's Changes against a new
 * revision's Changes (epic #6 §27): which are unchanged, changed, newly
 * added, or removed, keyed on {@link com.athena.semantic.ChangeIdentity}
 * rather than object/line identity — so this stays correct across a
 * force-push or rewritten commit history, not just a fast-forward commit.
 */
public final class ReviewContinuityReport {

    /** A prior Change matched to its corresponding new-revision Change by identity. */
    public record MatchedPair(Change priorChange, Change newChange) {
    }

    private final List<MatchedPair> unchanged;
    private final List<MatchedPair> changed;
    private final List<Change> added;
    private final List<Change> removed;

    ReviewContinuityReport(List<MatchedPair> unchanged, List<MatchedPair> changed,
                            List<Change> added, List<Change> removed) {
        this.unchanged = List.copyOf(unchanged);
        this.changed = List.copyOf(changed);
        this.added = List.copyOf(added);
        this.removed = List.copyOf(removed);
    }

    /** Same identity, same content (occurrence/exception count unchanged) between revisions. */
    public List<MatchedPair> unchanged() {
        return unchanged;
    }

    /** Same identity, different content (e.g. more/fewer occurrences) between revisions. */
    public List<MatchedPair> changed() {
        return changed;
    }

    /** No matching Change in the prior revision — newly introduced. */
    public List<Change> added() {
        return added;
    }

    /** A prior-revision Change with no match in the new revision — no longer present. */
    public List<Change> removed() {
        return removed;
    }

    /**
     * Builds a fresh {@link ReviewStateStore} for the new revision: unchanged Changes carry
     * their prior review state forward; changed and newly-added Changes start at
     * {@link ReviewState#UNSEEN} to prompt re-review, since the content a reviewer signed off
     * on either differs now or was never seen at all.
     */
    public ReviewStateStore carryForward(ReviewStateStore priorStore) {
        ReviewStateStore newStore = new ReviewStateStore();
        for (MatchedPair pair : unchanged) {
            ReviewState priorState = priorStore.stateOf(pair.priorChange());
            newStore.setState(pair.newChange(), priorState);
            if (priorStore.isMechanical(pair.priorChange())) {
                newStore.markMechanical(pair.newChange());
            }
        }
        // changed() and added() Changes are left at ReviewStateStore's own default (Unseen) —
        // no explicit state needs to be set for them to start fresh.
        return newStore;
    }
}
