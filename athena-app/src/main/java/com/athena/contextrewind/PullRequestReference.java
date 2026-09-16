package com.athena.contextrewind;

/**
 * A navigable pointer to a Pull Request a {@link ReconstructedContext}
 * drew from (ticket #161) — enough to open it directly, without the
 * caller needing to re-derive a URL from the number and repository
 * separately.
 */
public record PullRequestReference(int number, String repositoryFullName) {

    public String url() {
        return "https://github.com/" + repositoryFullName + "/pull/" + number;
    }
}
