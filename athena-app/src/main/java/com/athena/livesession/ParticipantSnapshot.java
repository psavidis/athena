package com.athena.livesession;

/**
 * A read-only view of one {@link Participant}, for {@link LiveReviewSessionSnapshot}.
 * {@code personalFocus} is {@code null} unless {@code mode} is {@code EXPLORING} —
 * an external/wire-facing shape, so a plain nullable field rather than
 * {@code Optional} (CODE_STYLE.md &sect;A.3).
 */
public record ParticipantSnapshot(
        String participantId,
        String displayName,
        boolean connected,
        ParticipantMode mode,
        CanvasFocus personalFocus) {

    static ParticipantSnapshot of(Participant participant) {
        return new ParticipantSnapshot(participant.id(), participant.displayName(), participant.connected(),
                participant.mode(), participant.personalFocus().orElse(null));
    }
}
