package com.athena.reviewbriefing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A developer's review briefing (ticket #218): structured data, not a
 * single generated paragraph, so each part can be independently linked
 * back to the Semantic Canvas. Groups into seven named sections — a
 * change summary, focus areas, uncertainties, questions, historical
 * context, relevant knowledge, and a recommended starting point — each a
 * list of {@link BriefingItem}s (the change summary and recommended
 * starting point are modeled as single-item sections rather than a
 * distinct shape, since they carry the exact same description/
 * entity-reference structure as everything else here).
 *
 * <p>An empty section is still present — {@link #focusAreas()} etc.
 * return an empty list, never null — so a renderer can distinguish
 * "nothing flagged here" from a section that was never built at all.
 *
 * <p>Defines the shape only; generating one is a later ticket's concern.
 */
public final class ReviewBriefing {

    private final BriefingItem changeSummary;
    private final List<BriefingItem> focusAreas;
    private final List<BriefingItem> uncertainties;
    private final List<BriefingItem> questions;
    private final List<BriefingItem> historicalContext;
    private final List<BriefingItem> relevantKnowledge;
    private final BriefingItem recommendedStartingPoint;

    private ReviewBriefing(Builder builder) {
        this.changeSummary = builder.changeSummary;
        this.focusAreas = List.copyOf(builder.focusAreas);
        this.uncertainties = List.copyOf(builder.uncertainties);
        this.questions = List.copyOf(builder.questions);
        this.historicalContext = List.copyOf(builder.historicalContext);
        this.relevantKnowledge = List.copyOf(builder.relevantKnowledge);
        this.recommendedStartingPoint = builder.recommendedStartingPoint;
    }

    public static Builder builder() {
        return new Builder();
    }

    public BriefingItem changeSummary() {
        return changeSummary;
    }

    public List<BriefingItem> focusAreas() {
        return focusAreas;
    }

    public List<BriefingItem> uncertainties() {
        return uncertainties;
    }

    public List<BriefingItem> questions() {
        return questions;
    }

    public List<BriefingItem> historicalContext() {
        return historicalContext;
    }

    public List<BriefingItem> relevantKnowledge() {
        return relevantKnowledge;
    }

    public BriefingItem recommendedStartingPoint() {
        return recommendedStartingPoint;
    }

    public static final class Builder {

        private BriefingItem changeSummary = BriefingItem.of("No change summary available.");
        private final List<BriefingItem> focusAreas = new ArrayList<>();
        private final List<BriefingItem> uncertainties = new ArrayList<>();
        private final List<BriefingItem> questions = new ArrayList<>();
        private final List<BriefingItem> historicalContext = new ArrayList<>();
        private final List<BriefingItem> relevantKnowledge = new ArrayList<>();
        private BriefingItem recommendedStartingPoint = BriefingItem.of("No recommendation available.");

        private Builder() {
        }

        public Builder changeSummary(BriefingItem changeSummary) {
            this.changeSummary = Objects.requireNonNull(changeSummary, "changeSummary");
            return this;
        }

        public Builder addFocusArea(BriefingItem focusArea) {
            focusAreas.add(Objects.requireNonNull(focusArea, "focusArea"));
            return this;
        }

        public Builder addUncertainty(BriefingItem uncertainty) {
            uncertainties.add(Objects.requireNonNull(uncertainty, "uncertainty"));
            return this;
        }

        public Builder addQuestion(BriefingItem question) {
            questions.add(Objects.requireNonNull(question, "question"));
            return this;
        }

        public Builder addHistoricalContext(BriefingItem item) {
            historicalContext.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        public Builder addRelevantKnowledge(BriefingItem item) {
            relevantKnowledge.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        public Builder recommendedStartingPoint(BriefingItem recommendedStartingPoint) {
            this.recommendedStartingPoint =
                    Objects.requireNonNull(recommendedStartingPoint, "recommendedStartingPoint");
            return this;
        }

        public ReviewBriefing build() {
            return new ReviewBriefing(this);
        }
    }
}
