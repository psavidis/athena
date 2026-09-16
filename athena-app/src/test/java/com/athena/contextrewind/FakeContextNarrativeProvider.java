package com.athena.contextrewind;

import java.util.List;

/**
 * Fake implementation of ContextNarrativeProvider standing in for real
 * network calls to an AI provider's API — the external system boundary
 * tests should not cross for real (Detroit-school exception).
 */
public class FakeContextNarrativeProvider implements ContextNarrativeProvider {

    private String narrativeToReturn = "This entity's history forms a coherent story.";
    private String lastEntityName;
    private List<String> lastHistoricalFacts;

    public void willReturnNarrative(String narrative) {
        this.narrativeToReturn = narrative;
    }

    public String lastEntityName() {
        return lastEntityName;
    }

    public List<String> lastHistoricalFacts() {
        return lastHistoricalFacts;
    }

    @Override
    public String narrate(String entityName, List<String> historicalFacts) {
        lastEntityName = entityName;
        lastHistoricalFacts = List.copyOf(historicalFacts);
        return narrativeToReturn;
    }
}
