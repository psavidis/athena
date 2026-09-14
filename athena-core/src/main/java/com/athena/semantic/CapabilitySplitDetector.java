package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recognizes a capability extraction: several of a PR's {@code
 * move-responsibility} classifications (see {@link ResponsibilityTaxonomyClassifier})
 * that all move from the same source module to the same destination module,
 * which together mean more than any one of them alone — a responsibility is
 * being carved out of the source module into a new or growing capability at
 * the destination, not just relocated in isolation.
 *
 * <p>A move's source/destination module is read from its evidence: {@link
 * TransformationDetector} in {@code athena-plugin-java} always records a
 * {@code MOVE_*} transformation's {@link DetectedTransformation#filesTouched()}
 * as {@code [beforeFile, afterFile]}, so {@link ModuleGrouper#buildModuleOf}
 * applied to each gives the module on either side of the move — the
 * finer-grained "module = the directory owning its own src/ root" reading,
 * not {@link ModuleGrouper#moduleOf}'s coarse top-level territory, so a move
 * between two sub-modules nested inside the same territory (e.g.
 * crowdness-domain-connect to crowdness-domain-ingestion, both under
 * crowdness-domain) is still recognized as crossing a module boundary.
 */
public final class CapabilitySplitDetector {

    private static final String MOVE_RESPONSIBILITY_CONCEPT_ID = "move-responsibility";
    private static final String CAPABILITY_EXTRACTION_CONCEPT_ID = "capability-extraction";
    private static final int MINIMUM_MOVES_TO_COUNT_AS_A_SPLIT = 2;

    private final Taxonomy responsibilityTaxonomy;

    public CapabilitySplitDetector(Taxonomy responsibilityTaxonomy) {
        if (responsibilityTaxonomy.dimension() != SemanticDimension.RESPONSIBILITY) {
            throw new IllegalArgumentException(
                    "Expected a RESPONSIBILITY taxonomy, got " + responsibilityTaxonomy.dimension());
        }
        this.responsibilityTaxonomy = responsibilityTaxonomy;
    }

    /**
     * Groups {@code profiles}' {@code move-responsibility} classifications by
     * (source module, destination module) pair. A pair reached by only one
     * move stays a plain move — {@link CapabilitySplitGroup} only ever
     * represents {@value #MINIMUM_MOVES_TO_COUNT_AS_A_SPLIT} or more.
     */
    public List<CapabilitySplitGroup> detect(List<SemanticProfile> profiles) {
        Map<ModulePair, List<Move>> byModulePair = new LinkedHashMap<>();
        for (SemanticProfile profile : profiles) {
            for (SemanticClassification classification : profile.classifications(SemanticDimension.RESPONSIBILITY)) {
                if (!classification.concept().id().equals(MOVE_RESPONSIBILITY_CONCEPT_ID)) {
                    continue;
                }
                modulePairOf(classification).ifPresent(pair -> byModulePair
                        .computeIfAbsent(pair, p -> new ArrayList<>())
                        .add(new Move(profile, classification)));
            }
        }

        List<CapabilitySplitGroup> groups = new ArrayList<>();
        for (Map.Entry<ModulePair, List<Move>> entry : byModulePair.entrySet()) {
            List<Move> moves = entry.getValue();
            if (moves.size() < MINIMUM_MOVES_TO_COUNT_AS_A_SPLIT) {
                continue;
            }
            groups.add(toGroup(entry.getKey(), moves));
        }
        return groups;
    }

    private CapabilitySplitGroup toGroup(ModulePair pair, List<Move> moves) {
        List<DetectedTransformation> evidence = new ArrayList<>();
        List<SemanticClassification> merged = new ArrayList<>();
        Set<String> flowConceptNames = new LinkedHashSet<>();
        for (Move move : moves) {
            evidence.addAll(move.classification.evidence());
            merged.add(move.classification);
            for (SemanticClassification flow : move.profile.classifications(SemanticDimension.FEATURE)) {
                flowConceptNames.add(flow.concept().name());
            }
        }

        TaxonomyConcept concept = responsibilityTaxonomy.find(CAPABILITY_EXTRACTION_CONCEPT_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Responsibility taxonomy is missing the \"" + CAPABILITY_EXTRACTION_CONCEPT_ID + "\" concept"));
        SemanticClassification classification = SemanticClassification.of(
                concept, evidence, List.copyOf(flowConceptNames));

        return new CapabilitySplitGroup(pair.sourceModule, pair.destinationModule, classification, merged);
    }

    private Optional<ModulePair> modulePairOf(SemanticClassification classification) {
        for (DetectedTransformation transformation : classification.evidence()) {
            List<String> files = transformation.filesTouched();
            if (files.size() >= 2) {
                String sourceModule = ModuleGrouper.buildModuleOf(files.get(0));
                String destinationModule = ModuleGrouper.buildModuleOf(files.get(1));
                if (!sourceModule.equals(destinationModule)) {
                    return Optional.of(new ModulePair(sourceModule, destinationModule));
                }
            }
        }
        return Optional.empty();
    }

    private record ModulePair(String sourceModule, String destinationModule) {
    }

    private record Move(SemanticProfile profile, SemanticClassification classification) {
    }
}
