package com.athena.analysis.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One finding from an {@link ExternalAnalysisProvider} (ticket #114/#148) —
 * provider-independent: an ESLint warning and a SonarJava blocker are both
 * represented the same way here. {@code providerSpecificMetadata} is the
 * one deliberate escape hatch for a provider's own terminology (e.g.
 * SonarJava's own rule-engine internals) — it is opaque to Athena's core,
 * which passes it through without interpreting it, so a provider never
 * needs Athena's core to understand its own concepts to be representable.
 *
 * <p>This is a different concept from {@code com.athena.ai.AiFinding}/
 * {@code AiFindingItem}: those are AI-surfaced findings with a reviewer
 * accept/dismiss disposition workflow, hardcoded to one AI source. An
 * {@link ExternalFinding} is raw static-analysis-tool output — no
 * disposition tracking, must support many simultaneous providers.
 */
public final class ExternalFinding {

    private final String providerId;
    private final String language;
    private final String ruleId;
    private final String ruleName;
    private final ExternalFindingSeverity severity;
    private final String message;
    private final SourceLocation location;
    private final List<SourceLocation> relatedLocations;
    private final Map<String, String> providerSpecificMetadata;

    private ExternalFinding(Builder builder) {
        this.providerId = builder.providerId;
        this.language = builder.language;
        this.ruleId = builder.ruleId;
        this.ruleName = builder.ruleName;
        this.severity = builder.severity;
        this.message = builder.message;
        this.location = builder.location;
        this.relatedLocations = List.copyOf(builder.relatedLocations);
        this.providerSpecificMetadata = Map.copyOf(builder.providerSpecificMetadata);
    }

    public static Builder builder(String providerId, String language, String ruleId, ExternalFindingSeverity severity,
                                   String message, SourceLocation location) {
        return new Builder(providerId, language, ruleId, severity, message, location);
    }

    public String providerId() {
        return providerId;
    }

    public String language() {
        return language;
    }

    public String ruleId() {
        return ruleId;
    }

    /** A human-readable name for this finding's rule, when the provider supplies one. Empty otherwise. */
    public Optional<String> ruleName() {
        return Optional.ofNullable(ruleName);
    }

    public ExternalFindingSeverity severity() {
        return severity;
    }

    public String message() {
        return message;
    }

    public SourceLocation location() {
        return location;
    }

    /** Secondary locations this finding also references (e.g. "declared here"). Empty if none. */
    public List<SourceLocation> relatedLocations() {
        return relatedLocations;
    }

    /**
     * Provider-specific metadata, opaque to Athena's core (e.g. a
     * provider's own rule category, engine version). Empty if the
     * provider supplies none.
     */
    public Map<String, String> providerSpecificMetadata() {
        return providerSpecificMetadata;
    }

    /** Builds an {@link ExternalFinding}, per CODE_STYLE.md's builder guidance for a type with several optional fields. */
    public static final class Builder {
        private final String providerId;
        private final String language;
        private final String ruleId;
        private final ExternalFindingSeverity severity;
        private final String message;
        private final SourceLocation location;
        private String ruleName;
        private List<SourceLocation> relatedLocations = List.of();
        private Map<String, String> providerSpecificMetadata = Map.of();

        private Builder(String providerId, String language, String ruleId, ExternalFindingSeverity severity,
                         String message, SourceLocation location) {
            this.providerId = requireNonBlank(providerId, "providerId");
            this.language = requireNonBlank(language, "language");
            this.ruleId = requireNonBlank(ruleId, "ruleId");
            this.severity = Objects.requireNonNull(severity, "severity");
            this.message = requireNonBlank(message, "message");
            this.location = Objects.requireNonNull(location, "location");
        }

        public Builder ruleName(String ruleName) {
            this.ruleName = ruleName;
            return this;
        }

        public Builder relatedLocations(List<SourceLocation> relatedLocations) {
            this.relatedLocations = Objects.requireNonNull(relatedLocations, "relatedLocations");
            return this;
        }

        public Builder providerSpecificMetadata(Map<String, String> providerSpecificMetadata) {
            this.providerSpecificMetadata = Objects.requireNonNull(providerSpecificMetadata, "providerSpecificMetadata");
            return this;
        }

        public ExternalFinding build() {
            return new ExternalFinding(this);
        }

        private static String requireNonBlank(String value, String fieldName) {
            Objects.requireNonNull(value, fieldName);
            if (value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value;
        }
    }
}
