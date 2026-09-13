package com.athena.plugin.spring;

import com.athena.semantic.Change;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.SemanticClassification;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.Taxonomy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        for (DetectedTransformation transformation : change.matchedOccurrences()) {
            Optional<String> conceptId = newlyAddedAnnotationConceptId(transformation.diffText());
            if (conceptId.isPresent()) {
                return conceptId.flatMap(frameworkTaxonomy::find)
                        .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences())));
            }
        }
        return Optional.empty();
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
