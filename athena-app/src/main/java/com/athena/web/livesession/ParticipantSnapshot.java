package com.athena.web.livesession;

import com.athena.livesession.Participant;
import com.athena.livesession.ParticipantMode;

/**
 * A read-only view of one {@link Participant}, for {@link LiveReviewSessionSnapshot}
 * (ticket #158). {@code personalFocus} is {@code null} unless {@code mode} is
 * {@code EXPLORING} — a plain nullable field rather than {@code Optional}
 * (CODE_STYLE.md &sect;D.1), since this is the wire shape sent as JSON.
 */
public record ParticipantSnapshot(
        String participantId,
        String displayName,
        boolean connected,
        ParticipantMode mode,
        CanvasFocusResponse personalFocus) {

    static ParticipantSnapshot of(Participant participant) {
        return new ParticipantSnapshot(participant.id(), participant.displayName(), participant.connected(),
                participant.mode(), participant.personalFocus().map(CanvasFocusResponse::of).orElse(null));
    }
}
