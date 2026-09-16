package com.athena.contextrewind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The result of reconstructing an entity's context (ticket #161): its
 * labeled {@link #facts()}, the Pull Requests it can be traced to
 * ({@link #pullRequestReferences()}), its chronological
 * {@link #evolutionTimeline()}, and — only when there was enough history
 * to say anything — an optional {@link #aiNarrative()}. When nothing
 * useful was found, {@link #insufficientHistoryMessage()} says so rather
 * than the result silently looking empty.
 */
public final class ReconstructedContext {

    private final String entityName;
    private final List<ContextFact> facts;
    private final List<PullRequestReference> pullRequestReferences;
    private final List<HistoricalActivity> evolutionTimeline;
    private final Optional<String> insufficientHistoryMessage;
    private final Optional<String> aiNarrative;

    private ReconstructedContext(Builder builder) {
        this.entityName = builder.entityName;
        this.facts = List.copyOf(builder.facts);
        this.pullRequestReferences = List.copyOf(builder.pullRequestReferences);
        this.evolutionTimeline = builder.evolutionTimeline.stream()
                .sorted(Comparator.comparing(HistoricalActivity::occurredAt))
                .toList();
        this.insufficientHistoryMessage = builder.insufficientHistoryMessage;
        this.aiNarrative = builder.aiNarrative;
    }

    public static Builder builder(String entityName) {
        return new Builder(entityName);
    }

    public String entityName() {
        return entityName;
    }

    public List<ContextFact> facts() {
        return facts;
    }

    public List<PullRequestReference> pullRequestReferences() {
        return pullRequestReferences;
    }

    /** This entity's history, oldest first. */
    public List<HistoricalActivity> evolutionTimeline() {
        return evolutionTimeline;
    }

    public boolean hasInsufficientHistory() {
        return insufficientHistoryMessage.isPresent();
    }

    public Optional<String> insufficientHistoryMessage() {
        return insufficientHistoryMessage;
    }

    public Optional<String> aiNarrative() {
        return aiNarrative;
    }

    /** Builds a {@link ReconstructedContext}, per CODE_STYLE.md's builder guidance for a type with several optional parts. */
    public static final class Builder {

        private final String entityName;
        private final List<ContextFact> facts = new ArrayList<>();
        private final List<PullRequestReference> pullRequestReferences = new ArrayList<>();
        private final List<HistoricalActivity> evolutionTimeline = new ArrayList<>();
        private Optional<String> insufficientHistoryMessage = Optional.empty();
        private Optional<String> aiNarrative = Optional.empty();

        private Builder(String entityName) {
            this.entityName = entityName;
        }

        public Builder fact(ContextFact fact) {
            facts.add(fact);
            return this;
        }

        public Builder pullRequestReference(PullRequestReference reference) {
            pullRequestReferences.add(reference);
            return this;
        }

        public Builder historicalActivity(HistoricalActivity activity) {
            evolutionTimeline.add(activity);
            return this;
        }

        public Builder insufficientHistoryMessage(String message) {
            this.insufficientHistoryMessage = Optional.of(message);
            return this;
        }

        public Builder aiNarrative(String narrative) {
            this.aiNarrative = Optional.of(narrative);
            return this;
        }

        public ReconstructedContext build() {
            return new ReconstructedContext(this);
        }
    }
}
