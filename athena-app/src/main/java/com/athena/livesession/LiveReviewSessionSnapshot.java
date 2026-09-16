package com.athena.livesession;

import java.util.List;

/**
 * A read-only view of a {@link LiveReviewSession} at one point in time —
 * what {@link LiveReviewSessionController} returns from every action, and
 * what its SSE stream pushes to every connected participant on every
 * change (ticket #158). {@code presenterId} is {@code null} exactly when
 * nobody currently holds shared-navigation control (its presenter left
 * without anyone taking control back) — an external/wire DTO, so a plain
 * nullable field rather than {@code Optional} (CODE_STYLE.md &sect;A.3).
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
        CanvasFocus sharedFocus,
        String presenterId,
        List<ParticipantSnapshot> participants,
        boolean ended) {
}
