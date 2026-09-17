package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticProfile;

import java.util.List;
import java.util.Optional;

/**
 * Generates a Review Briefing's {@link ReviewBriefing#changeSummary()}
 * from a PR's detected Changes and their semantic profiles (ticket #219).
 * A PR with no detected Changes has nothing to summarize — {@link
 * Optional#empty()} rather than a fabricated summary of nothing, the
 * same "present but empty/absent, not guessed" principle {@link
 * ReviewBriefing} itself follows.
 */
public class ChangeSummaryGenerator {

    private final SemanticChangeSummaryProvider provider;

    public ChangeSummaryGenerator(SemanticChangeSummaryProvider provider) {
        this.provider = provider;
    }

    /** The change-summary {@link BriefingItem} for {@code changes}/{@code profiles}, if there's anything to summarize. */
    public Optional<BriefingItem> generate(List<Change> changes, List<SemanticProfile> profiles) {
        if (changes.isEmpty()) {
            return Optional.empty();
        }
        String summary = provider.summarize(changes, profiles);
        return Optional.of(BriefingItem.of(summary));
    }
}
