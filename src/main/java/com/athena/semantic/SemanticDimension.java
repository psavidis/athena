package com.athena.semantic;

/**
 * One of the semantic dimensions a {@link Change} can be interpreted along
 * (ticket #86): mechanical fact, through implementation technique, framework
 * mechanism, responsibility, feature, architectural role, to developer
 * intent. These are coexisting dimensions, not a strict parent-child
 * hierarchy — a single Change can carry a classification in every one of
 * them at once (see {@link SemanticProfile}).
 *
 * <p>Each dimension owns the name of its taxonomy JSON resource (Strategy
 * Enum Pattern per CODE_STYLE.md §A.2) so adding a dimension can't silently
 * omit where its concepts are defined.
 */
public enum SemanticDimension {
    STRUCTURAL("taxonomy/structural.json"),
    PATTERN("taxonomy/pattern.json"),
    FRAMEWORK("taxonomy/framework.json"),
    RESPONSIBILITY("taxonomy/responsibility.json"),
    FEATURE("taxonomy/feature.json"),
    ARCHITECTURE("taxonomy/architecture.json"),
    INTENT("taxonomy/intent.json");

    private final String taxonomyResource;

    SemanticDimension(String taxonomyResource) {
        this.taxonomyResource = taxonomyResource;
    }

    /** Classpath location of this dimension's taxonomy JSON definition. */
    public String taxonomyResource() {
        return taxonomyResource;
    }
}
