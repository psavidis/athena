package com.athena.github;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fake implementation of GitHubGraphQLTransport standing in for real GraphQL
 * calls to api.github.com/graphql — the external system boundary tests
 * should not cross for real (Detroit-school exception).
 */
public class FakeGitHubGraphQLTransport implements GitHubGraphQLTransport {

    private record FileKey(String repo, int number, String path) {
    }

    private final Set<String> resolvableThreads = new HashSet<>();
    private final Set<String> resolvedThreads = new HashSet<>();
    private final Set<FileKey> viewableFiles = new HashSet<>();
    private final Set<FileKey> viewedFiles = new HashSet<>();
    private final List<String> resolveMutationCalls = new ArrayList<>();
    private final List<FileKey> markViewedMutationCalls = new ArrayList<>();
    private String rejectedToken;

    public void allowResolvingThread(String threadId) {
        resolvableThreads.add(threadId);
    }

    public void allowMarkingFileViewed(String repositoryFullName, int number, String path) {
        viewableFiles.add(new FileKey(repositoryFullName, number, path));
    }

    public void rejectToken(String token) {
        rejectedToken = token;
    }

    public Set<String> resolvedThreads() {
        return resolvedThreads;
    }

    public Set<String> viewedFiles(String repositoryFullName, int number) {
        Set<String> paths = new HashSet<>();
        for (FileKey key : viewedFiles) {
            if (key.repo().equals(repositoryFullName) && key.number() == number) {
                paths.add(key.path());
            }
        }
        return paths;
    }

    /** How many times the resolve-thread mutation actually reached this transport for the given thread. */
    public long resolveMutationCallCount(String threadId) {
        return resolveMutationCalls.stream().filter(threadId::equals).count();
    }

    /** How many times the mark-file-as-viewed mutation actually reached this transport for the given file. */
    public long markViewedMutationCallCount(String repositoryFullName, int number, String path) {
        FileKey key = new FileKey(repositoryFullName, number, path);
        return markViewedMutationCalls.stream().filter(key::equals).count();
    }

    @Override
    public void resolveReviewThread(String token, String threadId) {
        requireValidToken(token);
        if (!resolvableThreads.contains(threadId)) {
            throw new GitHubResourceNotFoundException("Review thread not found or not resolvable: " + threadId);
        }
        resolveMutationCalls.add(threadId);
        resolvedThreads.add(threadId);
    }

    @Override
    public void markFileAsViewed(String token, String repositoryFullName, int number, String path) {
        requireValidToken(token);
        FileKey key = new FileKey(repositoryFullName, number, path);
        if (!viewableFiles.contains(key)) {
            throw new GitHubResourceNotFoundException(
                    "File not found on pull request: " + path + " in " + repositoryFullName + "#" + number);
        }
        markViewedMutationCalls.add(key);
        viewedFiles.add(key);
    }

    private void requireValidToken(String token) {
        if (token != null && token.equals(rejectedToken)) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
        if ("invalid-token".equals(token)) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
    }
}
