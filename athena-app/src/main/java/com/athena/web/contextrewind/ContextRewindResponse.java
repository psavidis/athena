package com.athena.web.contextrewind;

import com.athena.contextrewind.ReconstructedContext;

import java.util.List;

/** {@link ReconstructedContext}, serialized for the frontend's story timeline & evidence panel (ticket #187). */
public record ContextRewindResponse(String entityName, List<TimelineEventResponse> evolutionTimeline,
                                     List<PullRequestReferenceResponse> pullRequestReferences, String aiNarrative,
                                     String insufficientHistoryMessage) {

    static ContextRewindResponse from(ReconstructedContext context) {
        List<TimelineEventResponse> timeline = context.evolutionTimeline().stream()
                .map(activity -> new TimelineEventResponse(activity.description(), activity.occurredAt()))
                .toList();
        List<PullRequestReferenceResponse> pullRequests = context.pullRequestReferences().stream()
                .map(reference -> new PullRequestReferenceResponse(reference.number(), reference.repositoryFullName(), reference.url()))
                .toList();
        return new ContextRewindResponse(context.entityName(), timeline, pullRequests,
                context.aiNarrative().orElse(null), context.insufficientHistoryMessage().orElse(null));
    }
}
