package com.athena.web.livesession;

import com.athena.livesession.CanvasFocus;

/**
 * The wire shape of a {@link CanvasFocus} (ticket #158) — plain nullable
 * fields rather than {@code Optional} (CODE_STYLE.md &sect;D.1), since this
 * travels directly over JSON as part of {@link LiveReviewSessionSnapshot}/
 * {@link ParticipantSnapshot}.
 */
public record CanvasFocusResponse(String zoomLevel, String selectedEntityId, String selectedChangeKey,
                                   String navigationContext) {

    static CanvasFocusResponse of(CanvasFocus focus) {
        return new CanvasFocusResponse(focus.zoomLevel(), focus.selectedEntityId().orElse(null),
                focus.selectedChangeKey().orElse(null), focus.navigationContext().orElse(null));
    }
}
