package com.athena.reviewbriefing;

import java.util.Objects;
import java.util.Optional;

/**
 * One item within a {@link ReviewBriefing} section (ticket #218): its
 * description text, and — wherever determinable — a reference to the
 * semantic entity (module/component/class/method/change) it concerns, so
 * it can be independently linked back to the Semantic Canvas. Not every
 * item concerns one specific entity (e.g. a repo-wide observation), so
 * the reference is optional, never mandatory.
 */
public final class BriefingItem {

    private final String description;
    private final String entityReference;

    private BriefingItem(String description, String entityReference) {
        this.description = description;
        this.entityReference = entityReference;
    }

    public static BriefingItem of(String description) {
        return of(description, null);
    }

    public static BriefingItem of(String description, String entityReference) {
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        return new BriefingItem(description, blankToNull(entityReference));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    public String description() {
        return description;
    }

    /** The semantic entity this item concerns, if one could be determined. */
    public Optional<String> entityReference() {
        return Optional.ofNullable(entityReference);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BriefingItem other)) return false;
        return description.equals(other.description) && Objects.equals(entityReference, other.entityReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, entityReference);
    }

    @Override
    public String toString() {
        return "BriefingItem[" + description + (entityReference != null ? ", " + entityReference : "") + "]";
    }
}
