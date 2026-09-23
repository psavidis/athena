package com.athena.semantic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Classifies a {@link Change} along the {@link SemanticDimension#PATTERN}
 * dimension using widely-recognized type-naming conventions (ticket #86's
 * "recognizable implementation techniques or design patterns"). Only fires
 * for {@code ADD_CLASS}/{@code RENAME_CLASS}, same reasoning as
 * {@link ArchitectureTaxonomyClassifier}: a naming convention only speaks
 * to a type actually taking on that name, not to arbitrary changes inside
 * an already-named type.
 *
 * <p>Patterns that can't be recognized this way — Strategy, Observer,
 * Decorator, and most of the taxonomy's other entries — genuinely need
 * multi-symbol structural shape recognition (e.g. "an interface with one
 * abstract method implemented by two unrelated types") that isn't available
 * from a single {@link Change} today, so they're deliberately left
 * unclassified rather than guessed at. Dependency Injection is the one
 * exception: see {@link #classifyDependencyInjection}.
 */
public final class PatternTaxonomyClassifier {

    private final Taxonomy patternTaxonomy;
    private final StructuralTaxonomyClassifier structuralClassifier;

    public PatternTaxonomyClassifier(Taxonomy patternTaxonomy, StructuralTaxonomyClassifier structuralClassifier) {
        if (patternTaxonomy.dimension() != SemanticDimension.PATTERN) {
            throw new IllegalArgumentException("Expected a PATTERN taxonomy, got " + patternTaxonomy.dimension());
        }
        this.patternTaxonomy = patternTaxonomy;
        this.structuralClassifier = structuralClassifier;
    }

    public Optional<SemanticClassification> classify(Change change) {
        if (change.kind() != TransformationKind.ADD_CLASS && change.kind() != TransformationKind.RENAME_CLASS) {
            return Optional.empty();
        }
        if (change.matchedOccurrences().isEmpty()) {
            return Optional.empty();
        }
        String simpleName = lastSegment(change.matchedOccurrences().get(0).involvedDescriptions());
        return conceptIdFor(simpleName)
                .flatMap(patternTaxonomy::find)
                .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences()),
                        structuralConceptNamesOf(change)));
    }

    /** The structural concept name(s) {@code change} itself carries (ticket #96's "supported by" list). */
    private List<String> structuralConceptNamesOf(Change change) {
        return structuralClassifier.classify(change)
                .map(classification -> List.of(classification.concept().name()))
                .orElse(List.of());
    }

    private static final List<String> INJECTION_ANNOTATIONS = List.of("@Autowired", "@Inject", "@Resource");

    /**
     * Correlates an {@code ADD_CONSTRUCTOR_PARAMETER} Change with either of the two
     * structural shapes "field injection -> constructor injection" (ticket #86's
     * dependency-injection example) can take, matched by the same "EnclosingType#name":
     *
     * <ul>
     *   <li>a {@code REMOVE_FIELD} Change for the same name — the field declaration was
     *       deleted outright; or a {@code CHANGE_FIELD_TYPE} Change for the same name — the
     *       field was re-declared with a different type (ticket #267 reports that as one
     *       type change rather than a remove + add); or</li>
     *   <li>a {@code CHANGE_FIELD_ANNOTATIONS} Change for the same name whose diff drops
     *       an injection annotation ({@code @Autowired}/{@code @Inject}/{@code @Resource})
     *       — the field stayed declared, only its annotation and the constructor changed
     *       (the common real-world shape).</li>
     * </ul>
     *
     * <p>Unlike {@link #classify}, this needs the full set of Changes from one analysis:
     * no single Change carries both halves of either correlation. Matched by name within
     * the same enclosing type only (not also by declared type, which isn't available from
     * either Change's {@link DetectedTransformation#involvedDescriptions()} today) — a
     * same-named field/parameter pair on the same class is already a narrow enough
     * coincidence that this doesn't need a type check to stay a genuine structural match
     * rather than a guess.
     *
     * @return a classification for each {@code ADD_CONSTRUCTOR_PARAMETER} Change that
     *         correlates with either shape, keyed by that Change
     */
    public Map<Change, SemanticClassification> classifyDependencyInjection(List<Change> changes) {
        Map<Change, SemanticClassification> classifications = new HashMap<>();
        Optional<TaxonomyConcept> diConcept = patternTaxonomy.find("dependency-injection");
        if (diConcept.isEmpty()) {
            return classifications;
        }

        Map<String, Change> correlatingChangesByDescription = new HashMap<>();
        for (Change change : changes) {
            if (change.kind() == TransformationKind.REMOVE_FIELD
                    || change.kind() == TransformationKind.CHANGE_FIELD_TYPE
                    || (change.kind() == TransformationKind.CHANGE_FIELD_ANNOTATIONS && droppedInjectionAnnotation(change))) {
                for (String description : descriptionsOf(change)) {
                    correlatingChangesByDescription.put(description, change);
                }
            }
        }

        for (Change change : changes) {
            if (change.kind() != TransformationKind.ADD_CONSTRUCTOR_PARAMETER) {
                continue;
            }
            Optional<Change> correlatingChange = descriptionsOf(change).stream()
                    .map(correlatingChangesByDescription::get)
                    .filter(Objects::nonNull)
                    .findFirst();
            correlatingChange.ifPresent(correlate -> {
                List<String> supportingConceptNames = new ArrayList<>(structuralConceptNamesOf(change));
                supportingConceptNames.addAll(structuralConceptNamesOf(correlate));
                classifications.put(change, SemanticClassification.of(diConcept.get(),
                        List.copyOf(change.matchedOccurrences()), supportingConceptNames));
            });
        }
        return classifications;
    }

    private boolean droppedInjectionAnnotation(Change change) {
        return change.matchedOccurrences().stream().anyMatch(t -> {
            List<String> removedLines = t.diffText().lines().filter(line -> line.startsWith("-")).toList();
            return INJECTION_ANNOTATIONS.stream().anyMatch(annotation -> removedLines.stream().anyMatch(line -> line.contains(annotation)));
        });
    }

    private List<String> descriptionsOf(Change change) {
        return change.matchedOccurrences().stream()
                .flatMap(t -> t.involvedDescriptions().stream())
                .toList();
    }

    private String lastSegment(List<String> involvedDescriptions) {
        return involvedDescriptions.get(involvedDescriptions.size() - 1);
    }

    private Optional<String> conceptIdFor(String typeSimpleName) {
        if (typeSimpleName.endsWith("Builder")) {
            return Optional.of("builder");
        }
        if (typeSimpleName.endsWith("Factory")) {
            return Optional.of("factory");
        }
        if (typeSimpleName.endsWith("Mapper") || typeSimpleName.endsWith("Converter")) {
            return Optional.of("mapper");
        }
        if (typeSimpleName.endsWith("Repository")) {
            return Optional.of("repository");
        }
        if (typeSimpleName.endsWith("Specification")) {
            return Optional.of("specification");
        }
        if (typeSimpleName.endsWith("Dto") || typeSimpleName.endsWith("DTO")) {
            return Optional.of("dto");
        }
        if (typeSimpleName.endsWith("Facade")) {
            return Optional.of("facade");
        }
        if (typeSimpleName.endsWith("Proxy")) {
            return Optional.of("proxy");
        }
        if (typeSimpleName.endsWith("Adapter")) {
            return Optional.of("adapter");
        }
        if (typeSimpleName.endsWith("Decorator")) {
            return Optional.of("decorator");
        }
        return Optional.empty();
    }
}
