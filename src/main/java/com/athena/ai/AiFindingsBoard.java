package com.athena.ai;

import com.athena.reviewui.ChangeDetailView;
import com.athena.semantic.Change;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lets a reviewer evaluate each AI-surfaced finding independently (epic #7
 * §35, ticket #48): every finding starts {@link AiFindingDisposition#PENDING}
 * and can be accepted or dismissed on its own — one finding's disposition
 * never affects another's. Accepting/dismissing only changes this board's
 * own in-memory state; it never touches
 * {@link com.athena.semantic.ReviewStateStore} or posts anything anywhere —
 * the human remains the one who acts on a finding, not the AI.
 */
public final class AiFindingsBoard {

    private final List<AiFinding> findings;
    private final List<Change> changes;
    private final Map<String, AiFindingDisposition> dispositions = new LinkedHashMap<>();

    private AiFindingsBoard(List<AiFinding> findings, List<Change> changes) {
        this.findings = List.copyOf(findings);
        this.changes = List.copyOf(changes);
    }

    /**
     * @param findings the candidate findings from an {@link AiProvider#analyze} call
     * @param changes  the PR's detected Changes, to resolve a finding's jump target against
     */
    public static AiFindingsBoard assemble(List<AiFinding> findings, List<Change> changes) {
        return new AiFindingsBoard(findings, changes);
    }

    public void accept(String findingId) {
        dispositions.put(requireKnownFinding(findingId), AiFindingDisposition.ACCEPTED);
    }

    public void dismiss(String findingId) {
        dispositions.put(requireKnownFinding(findingId), AiFindingDisposition.DISMISSED);
    }

    private String requireKnownFinding(String findingId) {
        boolean known = findings.stream().anyMatch(finding -> finding.id().equals(findingId));
        if (!known) {
            throw new IllegalArgumentException("No such finding: " + findingId);
        }
        return findingId;
    }

    public AiFindingDisposition dispositionOf(String findingId) {
        return dispositions.getOrDefault(findingId, AiFindingDisposition.PENDING);
    }

    /** Every finding as its own independently-evaluable item, clearly AI-attributed. */
    public List<AiFindingItem> items() {
        return findings.stream()
                .map(finding -> new AiFindingItem(finding, dispositionOf(finding.id()), jumpTargetFor(finding)))
                .toList();
    }

    /**
     * Matches by exact {@link Change#title()} string equality against Claude's own echoed-back
     * text (see {@link ClaudeAiProvider}) — the only reference a finding carries. Two Changes
     * sharing an identical title (e.g. the same rename pattern occurring independently in two
     * unrelated files) would resolve to whichever {@code findFirst()} happens to return, which
     * could point a finding at the wrong Change rather than at none. Accepted as an MVP
     * limitation: correctly disambiguating this would need a finding to carry a more precise
     * reference than free-text title matching, which this ticket's scope doesn't require.
     */
    private Optional<ChangeDetailView> jumpTargetFor(AiFinding finding) {
        return finding.relatedChangeTitle()
                .flatMap(title -> changes.stream().filter(change -> change.title().equals(title)).findFirst())
                .map(ChangeDetailView::of);
    }
}
