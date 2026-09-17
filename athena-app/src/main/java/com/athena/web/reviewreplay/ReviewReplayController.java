package com.athena.web.reviewreplay;

import com.athena.memory.MemoryEntry;
import com.athena.memory.ProjectMemoryStore;
import com.athena.reviewreplay.KnownEntityModuleResolver;
import com.athena.reviewreplay.KnownEntityReferenceResolver;
import com.athena.reviewreplay.OutcomePromotion;
import com.athena.reviewreplay.OutcomeSection;
import com.athena.reviewreplay.ReplayModuleResolver;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The web entry point for opening a Review Replay (ticket #210): loads a
 * persisted {@code ReviewRecordingArtifact} (ticket #207) and resolves its
 * moment references against the currently-selected PR/Diff's analyzed
 * entities. Also imports an artifact a teammate shared from their own
 * Athena instance (ticket #217). No timeline UI or transcript handling
 * yet — later tickets (#211+) build on this contract.
 */
@RestController
@RequestMapping("/api/review-replays")
public class ReviewReplayController {

    private final WebSession session;
    private final Function<Path, ReviewRecordingArtifactStore> artifactStoreFactory;
    private final Function<Diff, ReplayReferenceResolver> resolverFactory;
    private final Function<Diff, ReplayModuleResolver> moduleResolverFactory;

    @Autowired
    public ReviewReplayController(WebSession session) {
        this(session, ReviewRecordingArtifactStore::new,
                diff -> new KnownEntityReferenceResolver(
                        () -> diff.changes().stream().map(Change::enclosingType).collect(Collectors.toSet())),
                diff -> new KnownEntityModuleResolver(diff::changes));
    }

    /**
     * @param artifactStoreFactory  resolves the {@link ReviewRecordingArtifactStore} for a given project root
     * @param resolverFactory       builds the {@link ReplayReferenceResolver} for the currently-selected
     *                              {@link Diff} — overridable so a test can supply a resolver backed by a
     *                              known, fixed set of entity names instead of running full PR analysis
     *                              against a real (or fixture) codebase, which is production concern
     *                              {@link Diff#changes()} already owns
     * @param moduleResolverFactory builds the {@link ReplayModuleResolver} for the currently-selected
     *                              {@link Diff} (ticket #212), overridable for the same reason
     */
    ReviewReplayController(WebSession session, Function<Path, ReviewRecordingArtifactStore> artifactStoreFactory,
                            Function<Diff, ReplayReferenceResolver> resolverFactory,
                            Function<Diff, ReplayModuleResolver> moduleResolverFactory) {
        this.session = session;
        this.artifactStoreFactory = artifactStoreFactory;
        this.resolverFactory = resolverFactory;
        this.moduleResolverFactory = moduleResolverFactory;
    }

    @GetMapping("/{recordingId}")
    public ReviewReplayResponse open(@PathVariable String recordingId) {
        Diff diff = requireCurrentDiff();
        return ReviewReplayResponse.of(openReplay(recordingId, diff));
    }

    @PostMapping("/{recordingId}/outcome/promote")
    public void promoteOutcomeItem(@PathVariable String recordingId, @RequestBody PromoteOutcomeItemRequest request) {
        Diff diff = requireCurrentDiff();
        ReviewReplay replay = openReplay(recordingId, diff);
        OutcomeSection section;
        try {
            section = OutcomeSection.valueOf(request.section());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown outcome section: " + request.section());
        }
        MemoryEntry entry = OutcomePromotion.entryFor(replay, section, request.reference(), request.fact())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such outcome item"));
        new ProjectMemoryStore(diff.headRoot()).record(entry);
    }

    private ReviewReplay openReplay(String recordingId, Diff diff) {
        ReviewRecordingArtifact artifact = artifactStoreFactory.apply(diff.headRoot()).find(recordingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such Review Recording artifact"));
        return ReviewReplay.open(artifact, resolverFactory.apply(diff), moduleResolverFactory.apply(diff));
    }

    @PostMapping("/import")
    public ReviewReplayResponse importSharedArtifact(@RequestBody ImportSharedArtifactRequest request) {
        Diff diff = requireCurrentDiff();
        String repositoryFullName = session.selectedPullRequest()
                .map(WebSession.SelectedPullRequest::repositoryFullName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Select a Pull Request before importing a shared Review Replay artifact"));
        try {
            ReviewRecordingArtifact artifact = artifactStoreFactory.apply(diff.headRoot())
                    .importFrom(Paths.get(request.filePath()), repositoryFullName);
            return ReviewReplayResponse.of(
                    ReviewReplay.open(artifact, resolverFactory.apply(diff), moduleResolverFactory.apply(diff)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Diff requireCurrentDiff() {
        return session.currentDiff()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Select a Pull Request or Diff before opening a Review Replay"));
    }
}
