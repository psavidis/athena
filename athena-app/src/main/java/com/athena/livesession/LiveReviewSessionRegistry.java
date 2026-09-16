package com.athena.livesession;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Every currently-active {@link LiveReviewSession}, keyed by its id
 * (ticket #158). Deliberately an application-scoped singleton, not
 * {@code @SessionScope} like {@code WebSession} — a session created in one
 * browser must be joinable, by id, from a different browser session
 * entirely, which is exactly what this registry (and this registry alone)
 * makes possible.
 */
@Component
public class LiveReviewSessionRegistry {

    private final ConcurrentMap<String, LiveReviewSession> sessions = new ConcurrentHashMap<>();

    /** Starts and registers a new session about {@code repositoryFullName}#{@code pullRequestNumber}. */
    public LiveReviewSession create(String repositoryFullName, int pullRequestNumber, String creatorDisplayName) {
        LiveReviewSession session = LiveReviewSession.create(repositoryFullName, pullRequestNumber, creatorDisplayName);
        sessions.put(session.id(), session);
        return session;
    }

    public Optional<LiveReviewSession> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /** Removes a session from the registry — called once it has ended (see {@link LiveReviewSession#end}). */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
}
