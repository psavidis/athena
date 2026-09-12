package com.athena.semantic;

import java.util.Objects;

/**
 * A stable, queryable identifier for a {@link Symbol}, derived from its
 * fully-qualified structural position (package + enclosing type chain +
 * member signature) rather than from file path or line number.
 *
 * <p>Two symbols with the same structural position produce equal
 * {@code SymbolId}s even across separately-built models of the same source
 * (see epic #4's Change-identity requirement, which builds on this same
 * "identity by structure, not location" principle).
 */
public final class SymbolId {

    private final String value;

    private SymbolId(String value) {
        this.value = Objects.requireNonNull(value);
    }

    /** A type's identifier is simply its fully-qualified name. */
    static SymbolId forType(String fullyQualifiedTypeName) {
        return new SymbolId(fullyQualifiedTypeName);
    }

    /** A member's (method/field) identifier is its enclosing type id plus its own signature. */
    static SymbolId forMember(SymbolId enclosingTypeId, String memberSignature) {
        return new SymbolId(enclosingTypeId.value + "#" + memberSignature);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymbolId other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
