package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticProfile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Composes a PR's full {@link ReviewBriefing} from the four independent
 * generators tickets #219-#222 each built (change summary, focus areas,
 * uncertainty/questions, historical/Knowledge Base context) — this
 * ticket's (#223) own backend prerequisite, since none of those tickets
 * assembled the whole briefing or exposed it over HTTP. No new
 * generation logic of its own; {@link
 * ReviewBriefing#recommendedStartingPoint()} is left absent since no
 * ticket in this chain generates one yet.
 */
public class ReviewBriefingService {

    private final ChangeSummaryGenerator changeSummaryGenerator;
    private final FocusAreaGenerator focusAreaGenerator;
    private final UncertaintyAndQuestionsGenerator uncertaintyAndQuestionsGenerator;
    private final HistoricalContextGenerator historicalContextGenerator;

    public ReviewBriefingService(ChangeSummaryGenerator changeSummaryGenerator, FocusAreaGenerator focusAreaGenerator,
                                  UncertaintyAndQuestionsGenerator uncertaintyAndQuestionsGenerator,
                                  HistoricalContextGenerator historicalContextGenerator) {
        this.changeSummaryGenerator = changeSummaryGenerator;
        this.focusAreaGenerator = focusAreaGenerator;
        this.uncertaintyAndQuestionsGenerator = uncertaintyAndQuestionsGenerator;
        this.historicalContextGenerator = historicalContextGenerator;
    }

    public ReviewBriefing generate(List<Change> changes, List<SemanticProfile> profiles, Path projectRoot,
                                    String repositoryFullName) {
        ReviewBriefing.Builder builder = ReviewBriefing.builder();

        changeSummaryGenerator.generate(changes, profiles).ifPresent(builder::changeSummary);

        List<BriefingItem> focusAreas = focusAreaGenerator.generate(changes, profiles);
        focusAreas.forEach(builder::addFocusArea);

        UncertaintyAndQuestions uncertaintyAndQuestions = uncertaintyAndQuestionsGenerator.generate(changes, profiles);
        uncertaintyAndQuestions.uncertainties().forEach(builder::addUncertainty);
        uncertaintyAndQuestions.questions().forEach(builder::addQuestion);

        List<String> focusAreaEntityNames = focusAreas.stream()
                .map(BriefingItem::entityReference)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        HistoricalContext historicalContext =
                historicalContextGenerator.generate(focusAreaEntityNames, projectRoot, repositoryFullName);
        historicalContext.historicalContext().forEach(builder::addHistoricalContext);
        historicalContext.relevantKnowledge().forEach(builder::addRelevantKnowledge);

        return builder.build();
    }
}
