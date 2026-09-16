package com.athena.web.livesession;

import com.athena.livesession.LiveReviewSessionSnapshot;

/**
 * Returned when starting or joining a Live Code Review Session (ticket
 * #158): the caller's own new/resumed {@code participantId} — which they
 * must send back on every later action and on reconnecting — plus the
 * session's current state.
 */
public record LiveSessionJoinResponse(String sessionId, String participantId, LiveReviewSessionSnapshot snapshot) {
}
