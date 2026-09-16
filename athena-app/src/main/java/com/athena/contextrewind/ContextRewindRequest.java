package com.athena.contextrewind;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What to reconstruct context for, and from where (ticket #161): the
 * entity's name, the project's working copy (for git history), and the
 * repository it's hosted on (for Pull Requests). {@link #since(Instant)}
 * turns an ordinary reconstruction into a "catch me up" one, scoped to
 * activity after that point.
 */
public final class ContextRewindRequest {

    private final String entityName;
    private final Path projectRoot;
    private final String repositoryFullName;
    private final Optional<Instant> since;

    private ContextRewindRequest(String entityName, Path projectRoot, String repositoryFullName, Optional<Instant> since) {
        this.entityName = entityName;
        this.projectRoot = projectRoot;
        this.repositoryFullName = repositoryFullName;
        this.since = since;
    }

    public static ContextRewindRequest of(String entityName, Path projectRoot, String repositoryFullName) {
        Objects.requireNonNull(entityName, "entityName");
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(repositoryFullName, "repositoryFullName");
        return new ContextRewindRequest(entityName, projectRoot, repositoryFullName, Optional.empty());
    }

    /** Returns a copy of this request scoped to activity after {@code since} — a "catch me up" reconstruction. */
    public ContextRewindRequest since(Instant since) {
        Objects.requireNonNull(since, "since");
        return new ContextRewindRequest(entityName, projectRoot, repositoryFullName, Optional.of(since));
    }

    public String entityName() {
        return entityName;
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public String repositoryFullName() {
        return repositoryFullName;
    }

    public Optional<Instant> since() {
        return since;
    }
}
