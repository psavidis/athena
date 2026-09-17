package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticProfile;

import java.util.List;

/**
 * Generates {@link ReviewBriefing#uncertainties()}/{@link
 * ReviewBriefing#questions()} from a PR's detected Changes and their
 * semantic profiles (ticket #221). A PR with no detected Changes has
 * nothing to be uncertain about — empty lists (never {@code null})
 * without calling the provider, the same "don't fabricate, don't waste a
 * call" principle {@code ChangeSummaryGenerator} (#219) already follows.
 */
public class UncertaintyAndQuestionsGenerator {

    private final UncertaintyAndQuestionsProvider provider;

    public UncertaintyAndQuestionsGenerator(UncertaintyAndQuestionsProvider provider) {
        this.provider = provider;
    }

    public UncertaintyAndQuestions generate(List<Change> changes, List<SemanticProfile> profiles) {
        if (changes.isEmpty()) {
            return UncertaintyAndQuestions.none();
        }
        return provider.analyze(changes, profiles);
    }
}
