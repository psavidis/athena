package com.athena.web.livesession;

import com.athena.livesession.LiveReviewSession;

import java.util.List;

/**
 * A read-only view of a {@link LiveReviewSession} at one point in time —
 * what {@link LiveReviewSessionController} returns from every action, and
 * what its SSE stream pushes to every connected participant on every
 * change (ticket #158). {@code presenterId} is {@code null} exactly when
 * nobody currently holds shared-navigation control (its presenter left
 * without anyone taking control back) — a plain nullable field rather than
 * {@code Optional} (CODE_STYLE.md &sect;D.1), since this is the JSON wire
 * shape, mapped here from the domain aggregate rather than serialized
 * directly (this codebase's established DTO-mapping convention — see
 * {@code CanvasCommentResponse.of}).
 *
 * <p>{@code revision} increases by one on every state change this session
 * makes; a client can use it to ignore an out-of-order push arriving after
 * a newer one — the deterministic tie-break the ticket's technical
 * requirements ask for, without needing any client-side merge logic:
 * whichever change reaches {@link LiveReviewSession}'s synchronized
 * methods first is simply the one that happened first.
 */
public record LiveReviewSessionSnapshot(
        String sessionId,
        long revision,
        String repositoryFullName,
        int pullRequestNumber,
        CanvasFocusResponse sharedFocus,
        String presenterId,
        String creatorId,
        List<ParticipantSnapshot> participants,
        boolean ended) {

    /**
     * Synchronizes on {@code session} itself for the duration of this read: {@link LiveReviewSession}'s
     * own mutators each guard their state change with a {@code synchronized(this)} block using that
     * same monitor, so without this, reading {@code revision}/{@code presenterId}/{@code participants}/etc.
     * one call at a time here could observe a torn combination of two different, non-atomic mutations
     * interleaved with each other — not just a single moment in time. Cheap (a handful of field reads,
     * no I/O), unlike the real network I/O this class's own class doc explains {@link LiveReviewSession}
     * deliberately keeps outside its locked sections.
     */
    static LiveReviewSessionSnapshot of(LiveReviewSession session) {
        synchronized (session) {
            List<ParticipantSnapshot> participants =
                    session.participants().stream().map(ParticipantSnapshot::of).toList();
            return new LiveReviewSessionSnapshot(session.id(), session.revision(), session.repositoryFullName(),
                    session.pullRequestNumber(), CanvasFocusResponse.of(session.sharedFocus()),
                    session.presenterId().orElse(null), session.creatorId(), participants, session.ended());
        }
    }
}
