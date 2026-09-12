package com.athena.ai;

import com.athena.reviewcontext.ReviewContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake implementation of AiProvider standing in for real network calls to
 * an AI provider's API (e.g. Anthropic's) — the external system boundary
 * tests should not cross for real (Detroit-school exception).
 */
public class FakeAiProvider implements AiProvider {

    private final List<AiFinding> findingsToReturn = new ArrayList<>();
    private ReviewContext lastAnalyzedReviewContext;

    public void willReturnFinding(String id, String description) {
        findingsToReturn.add(new AiFinding(id, description));
    }

    public ReviewContext lastAnalyzedReviewContext() {
        return lastAnalyzedReviewContext;
    }

    @Override
    public List<AiFinding> analyze(ReviewContext reviewContext) {
        lastAnalyzedReviewContext = reviewContext;
        return List.copyOf(findingsToReturn);
    }
}
