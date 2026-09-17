package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticProfile;

import java.util.List;

/**
 * Fake implementation of UncertaintyAndQuestionsProvider standing in for
 * real network calls to an AI provider's API — the external system
 * boundary tests should not cross for real (Detroit-school exception).
 */
public class FakeUncertaintyAndQuestionsProvider implements UncertaintyAndQuestionsProvider {

    private UncertaintyAndQuestions resultToReturn = UncertaintyAndQuestions.none();
    private List<Change> lastAnalyzedChanges;
    private int callCount = 0;

    public void willReturn(UncertaintyAndQuestions result) {
        this.resultToReturn = result;
    }

    public List<Change> lastAnalyzedChanges() {
        return lastAnalyzedChanges;
    }

    public int callCount() {
        return callCount;
    }

    @Override
    public UncertaintyAndQuestions analyze(List<Change> changes, List<SemanticProfile> profiles) {
        lastAnalyzedChanges = changes;
        callCount++;
        return resultToReturn;
    }
}
