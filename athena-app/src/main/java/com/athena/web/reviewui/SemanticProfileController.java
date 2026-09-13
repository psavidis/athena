package com.athena.web.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.SemanticClassification;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.SemanticProfile;
import com.athena.web.ChangeKey;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Serves a Change's full Semantic Profile (ticket #94) so the Semantic Change
 * Explorer (issue #91) has real data to render. Reuses {@link WebSession}'s
 * per-selection cached {@link com.athena.semantic.AnalysisResult} — this
 * controller never re-runs detection.
 *
 * <p>Whether a dimension's classifications are Observed or Inferred isn't
 * modeled anywhere yet (no classifier records this itself): {@link
 * #OBSERVED_DIMENSIONS} hardcodes it here from each classifier's own
 * documented behavior — {@code StructuralTaxonomyClassifier} is a direct,
 * deterministic reading of a transformation's kind, while the rest
 * (naming-convention correlation, {@code ResponsibilityTaxonomyClassifier}'s
 * kind-to-capability mapping, or Intent's cross-dimension correlation) are
 * explicitly heuristic — ticket #97 §"Capability level" calls out Capability
 * specifically as Inferred with a confidence percentage, not Observed.
 * Confidence for an Inferred entry is a flat placeholder — no scoring model
 * exists yet (out of scope per #91's "do not implement a complete autonomous
 * semantic/intent inference engine") — while Observed is always 100%, per
 * #91 §9's own example ("Structural: Observed · 100%").
 */
@RestController
public class SemanticProfileController {

    private static final Set<SemanticDimension> OBSERVED_DIMENSIONS =
            EnumSet.of(SemanticDimension.STRUCTURAL);
    private static final int INFERRED_CONFIDENCE_PERCENT = 70;

    private final WebSession session;

    public SemanticProfileController(WebSession session) {
        this.session = session;
    }

    @GetMapping("/api/review/change-map/{changeKey}/semantic-profile")
    public SemanticProfileResponse semanticProfile(@PathVariable String changeKey) {
        WebSession.SelectedPullRequest selection = requireSelection();
        Change change = requireChange(changeKey, selection.changes());

        SemanticProfile profile = selection.semanticProfileFor(change);
        List<SemanticDimensionEntryResponse> entries = new ArrayList<>();
        for (SemanticDimension dimension : SemanticDimension.values()) {
            for (SemanticClassification classification : profile.classifications(dimension)) {
                entries.add(toEntry(dimension, classification));
            }
        }
        return new SemanticProfileResponse(entries);
    }

    private SemanticDimensionEntryResponse toEntry(SemanticDimension dimension, SemanticClassification classification) {
        boolean inferred = !OBSERVED_DIMENSIONS.contains(dimension);
        int confidencePercent = inferred ? INFERRED_CONFIDENCE_PERCENT : 100;
        List<String> evidence = classification.evidence().stream().map(DetectedTransformation::diffText).toList();
        return new SemanticDimensionEntryResponse(dimension, classification.concept().name(),
                classification.concept().description(), inferred, confidencePercent, evidence,
                classification.supportingConceptNames(), classification.beforeEvidenceCount());
    }

    private WebSession.SelectedPullRequest requireSelection() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));
    }

    private Change requireChange(String changeKey, List<Change> changes) {
        return ChangeKey.resolve(changeKey, changes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
    }
}
