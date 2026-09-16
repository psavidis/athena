package com.athena.web.livesession;

/**
 * Request body to join a Live Code Review Session (ticket #158).
 * {@code participantId} is {@code null} for a first-time join, and the
 * caller's own previously-issued id for a reconnect after a dropped
 * connection — nullable since this is an external/wire DTO (CODE_STYLE.md
 * &sect;D.1).
 */
public record JoinLiveSessionRequest(String displayName, String participantId) {
}
