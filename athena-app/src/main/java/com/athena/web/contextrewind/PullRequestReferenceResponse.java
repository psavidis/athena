package com.athena.web.contextrewind;

/** A Pull Request that touched the entity, serialized with a directly-openable URL (ticket #187). */
public record PullRequestReferenceResponse(int number, String repositoryFullName, String url) {
}
