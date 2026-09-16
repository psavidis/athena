package com.athena.web.reviewreplay;

import com.athena.reviewreplay.KnownEntityReferenceResolver;
import com.athena.reviewreplay.ReplayReferenceResolver;
import com.athena.reviewreplay.ReviewReplay;
import com.athena.reviewrecorder.ReviewRecordingArtifact;
import com.athena.reviewrecorder.ReviewRecordingArtifactStore;
import com.athena.semantic.Change;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The web entry point for opening a Review Replay (ticket #210): loads a
 * persisted {@code ReviewRecordingArtifact} (ticket #207) and resolves its
 * moment references against the currently-selected PR/Diff's analyzed
 * entities. No timeline UI or transcript handling yet — later tickets
 * (#211+) build on this contract.
 */
@RestController
@RequestMapping("/api/review-replays")
public class ReviewReplayController {

    private final WebSession session;
    private final Function<Path, ReviewRecordingArtifactStore> artifactStoreFactory;
    private final Function<Diff, ReplayReferenceResolver> resolverFactory;

    @Autowired
    public ReviewReplayController(WebSession session) {
        this(session, ReviewRecordingArtifactStore::new,
                diff -> new KnownEntityReferenceResolver(
                        () -> diff.changes().stream().map(Change::enclosingType).collect(Collectors.toSet())));
    }

    /**
     * @param artifactStoreFactory resolves the {@link ReviewRecordingArtifactStore} for a given project root
     * @param resolverFactory      builds the {@link ReplayReferenceResolver} for the currently-selected
     *                              {@link Diff} — overridable so a test can supply a resolver backed by a
     *                              known, fixed set of entity names instead of running full PR analysis
     *                              against a real (or fixture) codebase, which is production concern
     *                              {@link Diff#changes()} already owns
     */
    ReviewReplayController(WebSession session, Function<Path, ReviewRecordingArtifactStore> artifactStoreFactory,
                            Function<Diff, ReplayReferenceResolver> resolverFactory) {
        this.session = session;
        this.artifactStoreFactory = artifactStoreFactory;
        this.resolverFactory = resolverFactory;
    }

    @GetMapping("/{recordingId}")
    public ReviewReplayResponse open(@PathVariable String recordingId) {
        Diff diff = session.currentDiff()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Select a Pull Request or Diff before opening a Review Replay"));
        ReviewRecordingArtifact artifact = artifactStoreFactory.apply(diff.headRoot()).find(recordingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such Review Recording artifact"));
        return ReviewReplayResponse.of(ReviewReplay.open(artifact, resolverFactory.apply(diff)));
    }
}
