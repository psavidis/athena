package com.athena.web.reviewui;

import com.athena.semantic.ChangedFile;
import com.athena.semantic.RepresentationCoverage;
import com.athena.semantic.UnrepresentedFile;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * What the current selection's analysis does not represent (ticket #260): the changed files
 * no Change cites, with the reason for each, and the raw unified diff of any changed file —
 * so a reviewer can always reach code Athena could not classify (epic #4 §44: raw diff access
 * is never blocked by analysis outcome). Works the same for a selected PR Review or a
 * standalone Diff, without a GitHub connection, like {@link ModuleTopologyController}.
 */
@RestController
public class RepresentationCoverageController {

    private final WebSession session;

    public RepresentationCoverageController(WebSession session) {
        this.session = session;
    }

    @GetMapping("/api/review/unrepresented-files")
    public UnrepresentedFilesResponse unrepresentedFiles() {
        RepresentationCoverage coverage = requireSelection().representationCoverage();
        return new UnrepresentedFilesResponse(coverage.changedFiles().size(), coverage.representedFileCount(),
                coverage.unrepresentedFiles().stream().map(RepresentationCoverageController::toResponse).toList());
    }

    @GetMapping("/api/review/raw-diff")
    public RawDiffResponse rawDiff(@RequestParam String path) {
        ChangedFile file = requireSelection().representationCoverage().changedFile(path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a changed file: " + path));
        return new RawDiffResponse(file.path(), file.unifiedDiff());
    }

    private static UnrepresentedFileResponse toResponse(UnrepresentedFile unrepresented) {
        ChangedFile file = unrepresented.file();
        return new UnrepresentedFileResponse(file.path(), file.status().name(), file.linesChanged(), file.hunkCount(),
                unrepresented.reason().name(), unrepresented.reason().label());
    }

    private Diff requireSelection() {
        return session.currentDiff()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR or Diff selected"));
    }
}
