package com.athena.semantic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleGrouperTest {

    @Test
    void moduleOfReadsOnlyTheLeadingPathSegment() {
        assertThat(ModuleGrouper.moduleOf("crowdness-domain/crowdness-domain-connect/src/main/java/Thing.java"))
                .isEqualTo("crowdness-domain");
    }

    @Test
    void moduleOfFallsBackToRootForAPathWithNoLeadingSegment() {
        assertThat(ModuleGrouper.moduleOf("Thing.java")).isEqualTo("(root)");
    }

    @Test
    void buildModuleOfReadsTheSegmentImmediatelyBeforeSrc() {
        assertThat(ModuleGrouper.buildModuleOf("crowdness-domain/crowdness-domain-connect/src/main/java/Thing.java"))
                .isEqualTo("crowdness-domain-connect");
    }

    @Test
    void buildModuleOfMatchesModuleOfWhenTheModuleIsOnlyOneSegmentDeep() {
        assertThat(ModuleGrouper.buildModuleOf("connect/src/main/java/Thing.java")).isEqualTo("connect");
    }

    @Test
    void buildModuleOfFallsBackToModuleOfsRuleWhenThereIsNoSrcSegment() {
        assertThat(ModuleGrouper.buildModuleOf("crowdness-domain/Thing.java")).isEqualTo("crowdness-domain");
    }

    @Test
    void buildModuleOfFallsBackToRootForAPathWithNeitherASrcSegmentNorALeadingSegment() {
        assertThat(ModuleGrouper.buildModuleOf("Thing.java")).isEqualTo("(root)");
    }
}
