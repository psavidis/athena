package com.athena.livesession;

import java.util.Objects;
import java.util.Optional;

/**
 * Where on the Semantic Canvas a Live Code Review Session's shared
 * navigation currently points (ticket #158): a zoom level, and — depending
 * on that level — a selected semantic entity, a selected Change, and a
 * free-form navigation-context label for presence display (e.g. "Architecture
 * &middot; PaymentValidator"). Deliberately opaque to the backend beyond that:
 * {@code zoomLevel} and {@code selectedEntityId} are whatever the Semantic
 * Canvas frontend already uses (the zoom-altitude rail's stop id, and the
 * existing canvas-item id scheme from {@code canvasItemId.ts} /
 * {@link com.athena.reviewui.AnnotationScope#canvasItem}), and
 * {@code selectedChangeKey} is an existing {@link com.athena.web.ChangeKey}
 * encoding — this class relays them, it never interprets them. That keeps
 * the collaboration layer able to evolve independently of the semantic
 * canvas/analysis pipeline, per the ticket's own technical requirement.
 */
public final class CanvasFocus {

    private final String zoomLevel;
    private final String selectedEntityId;
    private final String selectedChangeKey;
    private final String navigationContext;

    private CanvasFocus(String zoomLevel, String selectedEntityId, String selectedChangeKey, String navigationContext) {
        this.zoomLevel = zoomLevel;
        this.selectedEntityId = selectedEntityId;
        this.selectedChangeKey = selectedChangeKey;
        this.navigationContext = navigationContext;
    }

    /**
     * @param zoomLevel          required — the canvas's current zoom-altitude stop
     * @param selectedEntityId   the selected semantic entity's canvas item id, if any
     * @param selectedChangeKey  the selected Change's {@link com.athena.web.ChangeKey} encoding, if any
     * @param navigationContext  a free-form label describing this focus for presence display, if any
     */
    public static CanvasFocus of(String zoomLevel, String selectedEntityId, String selectedChangeKey,
                                  String navigationContext) {
        Objects.requireNonNull(zoomLevel, "zoomLevel");
        if (zoomLevel.isBlank()) {
            throw new IllegalArgumentException("zoomLevel must not be blank");
        }
        return new CanvasFocus(zoomLevel, blankToNull(selectedEntityId), blankToNull(selectedChangeKey),
                blankToNull(navigationContext));
    }

    /** The neutral focus a session starts at, before its presenter has navigated anywhere yet. */
    public static CanvasFocus initial() {
        return new CanvasFocus("OVERVIEW", null, null, null);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    public String zoomLevel() {
        return zoomLevel;
    }

    public Optional<String> selectedEntityId() {
        return Optional.ofNullable(selectedEntityId);
    }

    public Optional<String> selectedChangeKey() {
        return Optional.ofNullable(selectedChangeKey);
    }

    public Optional<String> navigationContext() {
        return Optional.ofNullable(navigationContext);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CanvasFocus other)) return false;
        return zoomLevel.equals(other.zoomLevel)
                && Objects.equals(selectedEntityId, other.selectedEntityId)
                && Objects.equals(selectedChangeKey, other.selectedChangeKey)
                && Objects.equals(navigationContext, other.navigationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zoomLevel, selectedEntityId, selectedChangeKey, navigationContext);
    }

    @Override
    public String toString() {
        return "CanvasFocus[" + zoomLevel + (selectedEntityId != null ? ", " + selectedEntityId : "") + "]";
    }
}
