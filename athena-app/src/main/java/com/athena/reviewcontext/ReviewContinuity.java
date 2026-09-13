package com.athena.reviewcontext;

import com.athena.reviewcontext.ReviewContinuityReport.MatchedPair;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeIdentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes review continuity between two revisions' Change sets (epic #6
 * §27), matching by {@link ChangeIdentity} — symbol-set + transformation-
 * shape — rather than reimplementing identity matching itself (that model
 * comes from epic #4 ticket #20).
 */
public final class ReviewContinuity {

    private ReviewContinuity() {
    }

    public static ReviewContinuityReport compare(List<Change> priorChanges, List<Change> newChanges) {
        Map<ChangeIdentity, Change> newByIdentity = new LinkedHashMap<>();
        for (Change newChange : newChanges) {
            newByIdentity.put(ChangeIdentity.of(newChange), newChange);
        }

        List<MatchedPair> unchanged = new ArrayList<>();
        List<MatchedPair> changed = new ArrayList<>();
        List<Change> removed = new ArrayList<>();

        for (Change priorChange : priorChanges) {
            ChangeIdentity priorIdentity = ChangeIdentity.of(priorChange);
            Change matched = newByIdentity.remove(priorIdentity);
            if (matched == null) {
                removed.add(priorChange);
            } else if (sameContent(priorChange, matched)) {
                unchanged.add(new MatchedPair(priorChange, matched));
            } else {
                changed.add(new MatchedPair(priorChange, matched));
            }
        }

        // Whatever remains in newByIdentity had no prior match at all.
        List<Change> added = List.copyOf(newByIdentity.values());

        return new ReviewContinuityReport(unchanged, changed, added, removed);
    }

    /**
     * Two Changes sharing the same identity are the same conceptual transformation, but their
     * evidence extent can still differ between revisions (e.g. a mechanical replacement now
     * touching more or fewer files) — that's what distinguishes "changed" from "unchanged"
     * despite matching identity.
     */
    private static boolean sameContent(Change a, Change b) {
        return a.occurrenceCount() == b.occurrenceCount() && a.exceptionCount() == b.exceptionCount();
    }
}
