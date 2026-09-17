package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.SemanticProfile;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Generates {@link ReviewBriefing#focusAreas()}: the 2-4 parts of a PR's
 * Changes that most deserve a reviewer's attention (ticket #220). A new,
 * project-specific ranking heuristic this ticket defines — not an
 * existing Athena capability — kept deliberately conservative and
 * deterministic, reusing only existing {@link SemanticProfile} data
 * rather than adding new classification capability:
 *
 * <ol>
 *   <li>Score each Change: +2 per classification under {@link
 *       SemanticDimension#RESPONSIBILITY} or {@link
 *       SemanticDimension#ARCHITECTURE} (the ticket's own named examples
 *       of "significant" dimensions), +1 per classification under any
 *       other dimension, +1 more if the Change recurs ({@link
 *       Change#occurrenceCount()} &gt; 1) — a repeated change suggests
 *       wider impact than a one-off.</li>
 *   <li>Sort descending by score; ties broken by occurrence count
 *       descending, then by title, for a deterministic order.</li>
 *   <li>Take the top {@value #MAX_FOCUS_AREAS} — fewer if there are
 *       fewer Changes; never padded to a minimum.</li>
 * </ol>
 *
 * <p>Each resulting {@link BriefingItem} references its Change's {@link
 * Change#enclosingType()} as the semantic entity it concerns.
 */
public class FocusAreaGenerator {

    private static final int MAX_FOCUS_AREAS = 4;

    /** The ranked focus-area {@link BriefingItem}s for {@code changes}/{@code profiles}, most significant first. */
    public List<BriefingItem> generate(List<Change> changes, List<SemanticProfile> profiles) {
        return IntStream.range(0, changes.size())
                .mapToObj(i -> new ScoredChange(changes.get(i), score(profiles.get(i))))
                .sorted(Comparator.comparingInt(ScoredChange::score).reversed()
                        .thenComparing(sc -> sc.change().occurrenceCount(), Comparator.reverseOrder())
                        .thenComparing(sc -> sc.change().title()))
                .limit(MAX_FOCUS_AREAS)
                .map(ScoredChange::toBriefingItem)
                .toList();
    }

    private int score(SemanticProfile profile) {
        int score = 0;
        for (SemanticDimension dimension : SemanticDimension.values()) {
            int weight = (dimension == SemanticDimension.RESPONSIBILITY || dimension == SemanticDimension.ARCHITECTURE)
                    ? 2 : 1;
            score += weight * profile.classifications(dimension).size();
        }
        if (profile.change().occurrenceCount() > 1) {
            score += 1;
        }
        return score;
    }

    private record ScoredChange(Change change, int score) {
        BriefingItem toBriefingItem() {
            return BriefingItem.of(change.title(), change.enclosingType());
        }
    }
}
