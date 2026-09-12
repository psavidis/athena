package com.athena.ai;

import com.athena.reviewcontext.ReviewContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fake implementation of AiProvider standing in for real network calls to
 * an AI provider's API (e.g. Anthropic's) — the external system boundary
 * tests should not cross for real (Detroit-school exception).
 */
public class FakeAiProvider implements AiProvider {

    private final List<AiFinding> findingsToReturn = new ArrayList<>();
    private ReviewContext lastAnalyzedReviewContext;

    public void willReturnFinding(String id, String description) {
        findingsToReturn.add(new AiFinding(id, description, Optional.empty()));
    }

    public void willReturnFinding(String id, String description, String relatedChangeTitle) {
        findingsToReturn.add(new AiFinding(id, description, Optional.of(relatedChangeTitle)));
    }

    /** The ReviewContext this fake actually received on its last {@link #analyze} call. */
    public ReviewContext lastAnalyzedReviewContext() {
        return lastAnalyzedReviewContext;
    }

    @Override
    public List<AiFinding> analyze(ReviewContext reviewContext) {
        lastAnalyzedReviewContext = reviewContext;
        return List.copyOf(findingsToReturn);
    }
}
