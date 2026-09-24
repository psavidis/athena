package com.athena.plugin.spring;

import com.athena.semantic.Change;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.SemanticClassification;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.Taxonomy;
import com.athena.semantic.TaxonomyConcept;
import com.athena.semantic.TransformationKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies a {@link Change} along the {@link SemanticDimension#FRAMEWORK}
 * dimension by looking for a framework annotation newly introduced by the
 * change — a line added (a {@code +}-prefixed line in
 * {@link DetectedTransformation#diffText()}) that wasn't present before
 * (ticket #86: "this level must remain framework-specific rather than
 * treating framework mechanisms as generic design patterns").
 *
 * <p>Only fires when the annotation is actually new to the change, not
 * merely present — a Change touching a class that already carries
 * {@code @Service} doesn't mean this Change introduced Spring service
 * registration. {@code spring-field-to-constructor-injection} is
 * deliberately not covered here: recognizing it needs correlating a field
 * removal with a constructor parameter addition across two separate
 * Changes, which a single Change's diff can't establish on its own.
 */
public final class FrameworkTaxonomyClassifier {

    private static final Map<String, String> ANNOTATION_TO_CONCEPT_ID = annotationToConceptId();

    /**
     * @param frameworkTaxonomy must be the FRAMEWORK-dimension taxonomy — passed per call
     *        (rather than fixed at construction) because {@link
     *        com.athena.semantic.spi.FrameworkPlugin#classify} receives it from the
     *        caller (see {@link SpringFrameworkPlugin}), which owns loading it once.
     */
    public Optional<SemanticClassification> classify(Change change, Taxonomy frameworkTaxonomy) {
        if (frameworkTaxonomy.dimension() != SemanticDimension.FRAMEWORK) {
            throw new IllegalArgumentException("Expected a FRAMEWORK taxonomy, got " + frameworkTaxonomy.dimension());
        }
        if (change.matchedOccurrences().isEmpty()) {
            return Optional.empty();
        }
        Optional<SemanticClassification> configurationProperty = configurationProperty(change, frameworkTaxonomy);
        if (configurationProperty.isPresent()) {
            return configurationProperty;
        }
        for (DetectedTransformation transformation : change.matchedOccurrences()) {
            Optional<String> conceptId = newlyAddedAnnotationConceptId(transformation.diffText());
            if (conceptId.isPresent()) {
                return conceptId.flatMap(frameworkTaxonomy::find)
                        .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences())));
            }
        }
        return Optional.empty();
    }

    private static final List<String> INJECTION_ANNOTATIONS = List.of("@Autowired", "@Inject", "@Resource");
    private static final Pattern CONFIGURATION_PROPERTIES_PREFIX = Pattern.compile(
            "@ConfigurationProperties\\s*\\(\\s*(?:(?:prefix|value)\\s*=\\s*)?\"([^\"]*)\"");

    /**
     * An added or removed field of a {@code @ConfigurationProperties} class — or of a class nested
     * in one — is the configuration property a user sets (ticket #295). Its key is the annotation's
     * prefix, then each nested class's name, then the field's name, all in kebab case (Spring
     * Boot's relaxed binding; nested classes by class name only). The enclosing types come from
     * the {@link DetectedTransformation#ENCLOSING_TYPE_ANNOTATIONS} context the Java plugin records.
     */
    private Optional<SemanticClassification> configurationProperty(Change change, Taxonomy frameworkTaxonomy) {
        boolean added = change.kind() == TransformationKind.ADD_FIELD;
        if (!added && change.kind() != TransformationKind.REMOVE_FIELD) {
            return Optional.empty();
        }
        DetectedTransformation field = change.matchedOccurrences().get(0);
        String enclosingTypes = field.context().get(DetectedTransformation.ENCLOSING_TYPE_ANNOTATIONS);
        if (enclosingTypes == null) {
            return Optional.empty();
        }
        String description = field.involvedDescriptions().get(0);
        return propertyKey(enclosingTypes, description.substring(description.indexOf('#') + 1))
                .flatMap(key -> frameworkTaxonomy.find("spring-configuration-property").map(concept -> TaxonomyConcept.of(
                        concept.id(), SemanticDimension.FRAMEWORK,
                        (added ? "Spring: Configuration Property " : "Spring: Removed Configuration Property ") + key,
                        concept.description(), concept.parentId())))
                .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences())));
    }

    /** The property key, if one of {@code enclosingTypes} (outermost first) is {@code @ConfigurationProperties}. */
    static Optional<String> propertyKey(String enclosingTypes, String fieldName) {
        List<String> types = List.of(enclosingTypes.split("\n"));
        for (int i = types.size() - 1; i >= 0; i--) {
            String annotations = types.get(i).substring(types.get(i).indexOf('\t') + 1);
            if (!annotations.contains("@ConfigurationProperties")) {
                continue;
            }
            Matcher prefix = CONFIGURATION_PROPERTIES_PREFIX.matcher(annotations);
            List<String> segments = new ArrayList<>();
            if (prefix.find() && !prefix.group(1).isEmpty()) {
                segments.add(prefix.group(1));
            }
            for (String nested : types.subList(i + 1, types.size())) {
                segments.add(kebabCase(nested.substring(0, nested.indexOf('\t'))));
            }
            segments.add(kebabCase(fieldName));
            return Optional.of(String.join(".", segments));
        }
        return Optional.empty();
    }

    /** "awaitAsyncResultsOnStop" -> "await-async-results-on-stop"; "Listener" -> "listener". */
    private static String kebabCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    /**
     * Correlates an {@code ADD_CONSTRUCTOR_PARAMETER} Change with a {@code
     * CHANGE_FIELD_ANNOTATIONS} Change (same name, same enclosing type) whose diff
     * drops an injection annotation ({@code @Autowired}/{@code @Inject}/{@code
     * @Resource}) — the "field injection -> constructor injection" mechanism
     * transition (ticket #97 §"Framework level"). Mirrors {@link
     * com.athena.semantic.PatternTaxonomyClassifier#classifyDependencyInjection}'s
     * correlation shape, but for the FRAMEWORK dimension's literal before/after
     * mechanism rather than the PATTERN dimension's "supported by" list: no single
     * Change's diff carries both the removed field annotation and the added
     * constructor parameter, since {@link com.athena.semantic.ChangeGrouper} always
     * puts different {@link TransformationKind}s in different Changes.
     *
     * <p>Unlike {@link #classify}, a field removed outright (as opposed to just
     * losing its injection annotation) is deliberately not matched here — only the
     * annotation-drop shape reads unambiguously as "this field became
     * constructor-injected" rather than "this field was deleted for some other
     * reason".
     *
     * @return a classification for each {@code ADD_CONSTRUCTOR_PARAMETER} Change that
     *         correlates, keyed by that Change; its evidence lists the correlating
     *         (before) Change's matched occurrences first, then the added-parameter
     *         (after) Change's own — the literal before/after mechanism snippets a
     *         reviewer sees, in order
     */
    public Map<Change, SemanticClassification> classifySpringFieldToConstructorInjection(List<Change> changes,
                                                                                           Taxonomy frameworkTaxonomy) {
        if (frameworkTaxonomy.dimension() != SemanticDimension.FRAMEWORK) {
            throw new IllegalArgumentException("Expected a FRAMEWORK taxonomy, got " + frameworkTaxonomy.dimension());
        }
        Map<Change, SemanticClassification> classifications = new HashMap<>();
        Optional<TaxonomyConcept> concept = frameworkTaxonomy.find("spring-field-to-constructor-injection");
        if (concept.isEmpty()) {
            return classifications;
        }

        Map<String, Change> droppedInjectionChangesByDescription = new HashMap<>();
        for (Change change : changes) {
            if (change.kind() == TransformationKind.CHANGE_FIELD_ANNOTATIONS && droppedInjectionAnnotation(change)) {
                for (String description : descriptionsOf(change)) {
                    droppedInjectionChangesByDescription.put(description, change);
                }
            }
        }

        for (Change change : changes) {
            if (change.kind() != TransformationKind.ADD_CONSTRUCTOR_PARAMETER) {
                continue;
            }
            Optional<Change> correlatingChange = descriptionsOf(change).stream()
                    .map(droppedInjectionChangesByDescription::get)
                    .filter(Objects::nonNull)
                    .findFirst();
            correlatingChange.ifPresent(correlate -> {
                List<DetectedTransformation> evidence = new ArrayList<>(correlate.matchedOccurrences());
                int beforeEvidenceCount = evidence.size();
                evidence.addAll(change.matchedOccurrences());
                classifications.put(change,
                        SemanticClassification.of(concept.get(), evidence, List.of(), beforeEvidenceCount));
            });
        }
        return classifications;
    }

    /** Each Spring *Aware callback setter, by method name, and the type it hands over. */
    private static final Map<String, String> AWARE_SETTER_TYPES = Map.of(
            "setBeanFactory", "BeanFactory",
            "setBeanClassLoader", "ClassLoader",
            "setEnvironment", "Environment",
            "setResourceLoader", "ResourceLoader");

    /**
     * Correlates a removed {@code *Aware} callback setter ({@code setBeanFactory}, {@code
     * setBeanClassLoader}, {@code setEnvironment}, {@code setResourceLoader}) with an {@code
     * ADD_CONSTRUCTOR_PARAMETER} Change in the same class whose added constructor receives the
     * type that setter handed over — the "Aware callback -> constructor injection" migration
     * (ticket #294). A removed setter with no such constructor parameter isn't classified: the
     * dependency may simply no longer be needed.
     *
     * @return a classification for each correlating {@code ADD_CONSTRUCTOR_PARAMETER} Change;
     *         its evidence lists the removed setter (before) first, then the added parameter (after)
     */
    public Map<Change, SemanticClassification> classifySpringAwareToConstructorInjection(List<Change> changes,
                                                                                           Taxonomy frameworkTaxonomy) {
        if (frameworkTaxonomy.dimension() != SemanticDimension.FRAMEWORK) {
            throw new IllegalArgumentException("Expected a FRAMEWORK taxonomy, got " + frameworkTaxonomy.dimension());
        }
        Map<Change, SemanticClassification> classifications = new HashMap<>();
        Optional<TaxonomyConcept> concept = frameworkTaxonomy.find("spring-aware-to-constructor-injection");
        if (concept.isEmpty()) {
            return classifications;
        }
        for (Change removal : changes) {
            if (removal.kind() != TransformationKind.REMOVE_SYMBOL) {
                continue;
            }
            String removed = descriptionsOf(removal).get(0);
            String type = AWARE_SETTER_TYPES.get(removed.substring(removed.indexOf('#') + 1));
            if (type == null) {
                continue;
            }
            String enclosingType = removal.enclosingType();
            changes.stream()
                    .filter(change -> change.kind() == TransformationKind.ADD_CONSTRUCTOR_PARAMETER)
                    .filter(change -> change.enclosingType().equals(enclosingType))
                    .filter(change -> receivesType(change, type))
                    .findFirst()
                    .ifPresent(addition -> {
                        List<DetectedTransformation> evidence = new ArrayList<>(removal.matchedOccurrences());
                        int beforeEvidenceCount = evidence.size();
                        evidence.addAll(addition.matchedOccurrences());
                        classifications.put(addition,
                                SemanticClassification.of(concept.get(), evidence, List.of(), beforeEvidenceCount));
                    });
        }
        return classifications;
    }

    /** Whether the added constructor's declaration takes a parameter of {@code type}. */
    private boolean receivesType(Change constructorParameter, String type) {
        Pattern parameterOfType = Pattern.compile("[(,]\\s*(?:final\\s+)?(?:[\\w.]+\\.)?" + type + "\\s+\\w+\\s*[,)]");
        return constructorParameter.matchedOccurrences().stream().anyMatch(t -> t.diffText().lines()
                .filter(line -> line.startsWith("+"))
                .anyMatch(line -> parameterOfType.matcher(line).find()));
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

    private Optional<String> newlyAddedAnnotationConceptId(String diffText) {
        List<String> lines = List.of(diffText.split("\n"));
        for (String line : lines) {
            if (!line.startsWith("+")) {
                continue;
            }
            for (Map.Entry<String, String> entry : ANNOTATION_TO_CONCEPT_ID.entrySet()) {
                if (line.contains(entry.getKey()) && !removedLineAlsoContains(lines, entry.getKey())) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * True if the annotation also appears on a {@code -}-prefixed line — meaning it was
     * already present before the change (its arguments may have changed, or it moved to a
     * different line), so a matching {@code +}-line doesn't mean the annotation is new.
     */
    private boolean removedLineAlsoContains(List<String> lines, String annotationName) {
        return lines.stream().anyMatch(line -> line.startsWith("-") && line.contains(annotationName));
    }

    private static Map<String, String> annotationToConceptId() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("@Service", "spring-service");
        map.put("@Repository", "spring-repository");
        map.put("@Component", "spring-component");
        map.put("@Configuration", "spring-configuration");
        map.put("@Bean", "spring-configuration");
        map.put("@Transactional", "spring-transaction-management");
        map.put("@OneToMany", "jpa-entity-relationship");
        map.put("@ManyToOne", "jpa-entity-relationship");
        map.put("@ManyToMany", "jpa-entity-relationship");
        map.put("@OneToOne", "jpa-entity-relationship");
        map.put("@JsonProperty", "jackson-serialization");
        map.put("@JsonCreator", "jackson-serialization");
        map.put("@JsonIgnore", "jackson-serialization");
        map.put("@BeforeEach", "junit-lifecycle");
        map.put("@AfterEach", "junit-lifecycle");
        map.put("@ExtendWith", "junit-lifecycle");
        return map;
    }
}
