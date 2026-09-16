package com.athena.web.contextrewind;

import com.athena.contextrewind.ContextFact;
import com.athena.contextrewind.ContextSource;
import com.athena.contextrewind.ReconstructedContext;

import java.util.List;

/** {@link ReconstructedContext}, serialized for the frontend's story timeline, evidence panel, and
 * Context Layers (tickets #187, #190). */
public record ContextRewindResponse(String entityName, List<TimelineEventResponse> evolutionTimeline,
                                     List<PullRequestReferenceResponse> pullRequestReferences, String aiNarrative,
                                     String insufficientHistoryMessage, List<String> knowledgeFacts) {

    static ContextRewindResponse from(ReconstructedContext context) {
        List<TimelineEventResponse> timeline = context.evolutionTimeline().stream()
                .map(activity -> new TimelineEventResponse(activity.description(), activity.occurredAt()))
                .toList();
        List<PullRequestReferenceResponse> pullRequests = context.pullRequestReferences().stream()
                .map(reference -> new PullRequestReferenceResponse(reference.number(), reference.repositoryFullName(), reference.url()))
                .toList();
        List<String> knowledgeFacts = context.facts().stream()
                .filter(fact -> fact.source() == ContextSource.KNOWLEDGE_BASE)
                .map(ContextFact::description)
                .toList();
        return new ContextRewindResponse(context.entityName(), timeline, pullRequests,
                context.aiNarrative().orElse(null), context.insufficientHistoryMessage().orElse(null), knowledgeFacts);
    }
}
