package com.athena.web.livesession;

/**
 * Request body to move a Live Code Review Session's shared focus
 * (presenter only) or a participant's own personal exploration focus
 * (ticket #158). {@code selectedEntityId}/{@code selectedChangeKey}/
 * {@code navigationContext} are nullable — an external/wire DTO
 * (CODE_STYLE.md &sect;D.1).
 */
public record LiveFocusRequest(
        String participantId,
        String zoomLevel,
        String selectedEntityId,
        String selectedChangeKey,
        String navigationContext) {
}
