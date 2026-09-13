package com.athena.semantic.spi;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticClassification;
import com.athena.semantic.Taxonomy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A pluggable framework/ecosystem backend contributing {@link
 * com.athena.semantic.SemanticDimension#FRAMEWORK}-dimension classifications
 * only (e.g. recognizing Spring/JPA/Jackson/JUnit annotations) — not
 * architecture or pattern classification, which stay core and
 * language-agnostic naming-convention classifiers. Spring is the first
 * implementation ({@code athena-plugin-spring}); a future framework (e.g.
 * Quarkus, or a Python framework) is a new module implementing this
 * interface, discovered via {@link java.util.ServiceLoader} — no change to
 * {@code athena-core} or the application is required.
 */
public interface FrameworkPlugin {

    /** A short, stable identifier for this framework, e.g. {@code "spring-boot"}. */
    String frameworkId();

    /**
     * The {@link LanguagePlugin#languageId()} values this framework plugin applies
     * to (e.g. {@code Set.of("java")} for Spring) — a framework plugin only runs
     * against Changes detected by a matching language plugin.
     */
    Set<String> supportedLanguageIds();

    /** Classifies {@code change} along the FRAMEWORK dimension, if this framework recognizes it. */
    Optional<SemanticClassification> classify(Change change, Taxonomy frameworkTaxonomy);

    /**
     * Classifies FRAMEWORK-dimension mechanism transitions that need more than one
     * Change to recognize (e.g. Spring's field-to-constructor injection: no single
     * Change's diff carries both the removed field annotation and the added
     * constructor parameter — ticket #97 §"Framework level"). Default empty for a
     * framework plugin with no such correlated classification.
     *
     * @return classifications keyed by whichever Change in {@code changes} they apply to
     */
    default Map<Change, SemanticClassification> classifyCorrelated(List<Change> changes, Taxonomy frameworkTaxonomy) {
        return Map.of();
    }
}
