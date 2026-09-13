package com.athena.ai;

import com.athena.semantic.ModuleGroup;

/**
 * Fake implementation of ModuleNarrativeProvider standing in for real
 * network calls to an AI provider's API — the external system boundary
 * tests should not cross for real (Detroit-school exception).
 */
public class FakeModuleNarrativeProvider implements ModuleNarrativeProvider {

    private String narrativeToReturn = "This module's Changes work together.";
    private ModuleGroup lastExplainedGroup;
    private int callCount = 0;

    public void willReturnNarrative(String narrative) {
        this.narrativeToReturn = narrative;
    }

    public ModuleGroup lastExplainedGroup() {
        return lastExplainedGroup;
    }

    public int callCount() {
        return callCount;
    }

    @Override
    public String explain(ModuleGroup moduleGroup) {
        lastExplainedGroup = moduleGroup;
        callCount++;
        return narrativeToReturn;
    }
}
