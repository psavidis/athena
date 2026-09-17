package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticProfile;

import java.util.List;

/**
 * Fake implementation of SemanticChangeSummaryProvider standing in for
 * real network calls to an AI provider's API — the external system
 * boundary tests should not cross for real (Detroit-school exception).
 */
public class FakeSemanticChangeSummaryProvider implements SemanticChangeSummaryProvider {

    private String summaryToReturn = "This PR refactors the retry logic.";
    private List<Change> lastSummarizedChanges;
    private int callCount = 0;

    public void willReturnSummary(String summary) {
        this.summaryToReturn = summary;
    }

    public List<Change> lastSummarizedChanges() {
        return lastSummarizedChanges;
    }

    public int callCount() {
        return callCount;
    }

    @Override
    public String summarize(List<Change> changes, List<SemanticProfile> profiles) {
        lastSummarizedChanges = changes;
        callCount++;
        return summaryToReturn;
    }
}
