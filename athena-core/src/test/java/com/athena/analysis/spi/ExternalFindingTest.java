package com.athena.analysis.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalFindingTest {

    private static final SourceLocation LOCATION = SourceLocation.ofLine("Order.java", 42);

    @Test
    void carriesTheRequiredFields() {
        ExternalFinding finding = ExternalFinding
                .builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "Avoid null dereference", LOCATION)
                .build();

        assertThat(finding.providerId()).isEqualTo("sonarjava");
        assertThat(finding.language()).isEqualTo("java");
        assertThat(finding.ruleId()).isEqualTo("S1234");
        assertThat(finding.severity()).isEqualTo(ExternalFindingSeverity.HIGH);
        assertThat(finding.message()).isEqualTo("Avoid null dereference");
        assertThat(finding.location()).isEqualTo(LOCATION);
    }

    @Test
    void optionalFieldsDefaultToEmptyWhenNotSet() {
        ExternalFinding finding = ExternalFinding
                .builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "Avoid null dereference", LOCATION)
                .build();

        assertThat(finding.ruleName()).isEmpty();
        assertThat(finding.relatedLocations()).isEmpty();
        assertThat(finding.providerSpecificMetadata()).isEmpty();
    }

    @Test
    void carriesRuleNameRelatedLocationsAndProviderSpecificMetadataWhenSupplied() {
        SourceLocation related = SourceLocation.ofLine("Order.java", 10);

        ExternalFinding finding = ExternalFinding
                .builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "Avoid null dereference", LOCATION)
                .ruleName("Null Dereference")
                .relatedLocations(List.of(related))
                .providerSpecificMetadata(Map.of("engineVersion", "10.4"))
                .build();

        assertThat(finding.ruleName()).contains("Null Dereference");
        assertThat(finding.relatedLocations()).containsExactly(related);
        assertThat(finding.providerSpecificMetadata()).containsEntry("engineVersion", "10.4");
    }

    @Test
    void rejectsABlankProviderId() {
        assertThatThrownBy(() ->
                ExternalFinding.builder("  ", "java", "S1234", ExternalFindingSeverity.HIGH, "message", LOCATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankMessage() {
        assertThatThrownBy(() ->
                ExternalFinding.builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "  ", LOCATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void twoSeparatelyBuiltFindingsWithTheSameFieldsAreEqual() {
        ExternalFinding first = ExternalFinding
                .builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "message", LOCATION)
                .build();
        ExternalFinding second = ExternalFinding
                .builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "message", LOCATION)
                .build();

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @Test
    void findingsWithDifferentRuleIdsAreNotEqual() {
        ExternalFinding first = ExternalFinding
                .builder("sonarjava", "java", "S1234", ExternalFindingSeverity.HIGH, "message", LOCATION)
                .build();
        ExternalFinding second = ExternalFinding
                .builder("sonarjava", "java", "S9999", ExternalFindingSeverity.HIGH, "message", LOCATION)
                .build();

        assertThat(first).isNotEqualTo(second);
    }
}
