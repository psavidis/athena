package com.athena.reviewreplay;

import com.athena.reviewrecorder.Moment;
import com.athena.reviewrecorder.MomentKind;
import com.athena.reviewrecorder.MomentStatus;

import java.util.List;
import java.util.Objects;

/**
 * A Replay's concise end-of-review outcome (ticket #215): what was
 * learned, decided, left unresolved, and what actions remain — a view
 * derived from a recording's confirmed {@link Moment}s, not a new
 * canonical record. A pending or rejected moment never appears here
 * (only {@link MomentStatus#CONFIRMED} moments are durable per #206's
 * own design).
 *
 * <p>"Unresolved" is every confirmed {@link MomentKind#QUESTION}/{@link
 * MomentKind#CONCERN} moment, without exception: today's moment model has
 * no field or relationship linking a question/concern to whatever later
 * resolved it (moments are flat, independent records), and building one
 * would be new architectural capability this view-only ticket explicitly
 * rules out — see the ticket's own re-scoping note.
 */
public final class ReviewOutcome {

    private final List<OutcomeItem> learned;
    private final List<OutcomeItem> decided;
    private final List<OutcomeItem> unresolved;
    private final List<OutcomeItem> actions;

    private ReviewOutcome(List<OutcomeItem> learned, List<OutcomeItem> decided, List<OutcomeItem> unresolved,
                           List<OutcomeItem> actions) {
        this.learned = learned;
        this.decided = decided;
        this.unresolved = unresolved;
        this.actions = actions;
    }

    /** Derives a {@link ReviewOutcome} from {@code moments}, a Replay's full (possibly not-yet-confirmed) moment list. */
    public static ReviewOutcome derive(List<Moment> moments) {
        Objects.requireNonNull(moments, "moments");
        List<Moment> confirmed = moments.stream().filter(m -> m.status() == MomentStatus.CONFIRMED).toList();
        return new ReviewOutcome(
                itemsOfKind(confirmed, MomentKind.INSIGHT),
                itemsOfKind(confirmed, MomentKind.DECISION),
                itemsOfAnyKind(confirmed, MomentKind.QUESTION, MomentKind.CONCERN),
                itemsOfKind(confirmed, MomentKind.ACTION));
    }

    private static List<OutcomeItem> itemsOfKind(List<Moment> confirmed, MomentKind kind) {
        return confirmed.stream().filter(m -> m.kind() == kind).map(OutcomeItem::from).toList();
    }

    private static List<OutcomeItem> itemsOfAnyKind(List<Moment> confirmed, MomentKind first, MomentKind second) {
        return confirmed.stream().filter(m -> m.kind() == first || m.kind() == second).map(OutcomeItem::from).toList();
    }

    public List<OutcomeItem> learned() {
        return learned;
    }

    public List<OutcomeItem> decided() {
        return decided;
    }

    public List<OutcomeItem> unresolved() {
        return unresolved;
    }

    public List<OutcomeItem> actions() {
        return actions;
    }
}
