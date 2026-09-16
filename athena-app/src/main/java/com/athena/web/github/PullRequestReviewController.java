package com.athena.web.github;

import com.athena.github.ImportedReviewData;
import com.athena.github.ReviewDataImporter;
import com.athena.web.WebSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Function;

/**
 * A Pull Request's existing review comments and reviewer verdicts (ticket
 * #192), reachable from a Pull Request referenced in Context Rewind.
 * Unlike {@code com.athena.web.contextrewind.ContextRewindController}, this
 * takes the repository/PR to look up directly rather than from the
 * session's current selection: a referenced Pull Request isn't necessarily
 * the one currently selected for review.
 *
 * <p><b>Known limitation:</b> {@link ImportedReviewData}'s comments are a
 * flat, unordered list (author, body, file path only) — no reply-
 * threading, no timestamps, no diff position, and no way to distinguish a
 * "question" comment from an "answer" or to correlate a comment to a
 * resulting code change. This endpoint serves that same flat data; it does
 * not attempt to reconstruct a structured reasoning trail.
 */
@RestController
public class PullRequestReviewController {

    private final WebSession session;
    private final Function<String, ReviewDataImporter> reviewDataImporterFactory;

    @Autowired
    public PullRequestReviewController(WebSession session) {
        this(session, ReviewDataImporter::new);
    }

    /** Test seam: a fake token-to-importer factory stands in for GitHub. */
    PullRequestReviewController(WebSession session, Function<String, ReviewDataImporter> reviewDataImporterFactory) {
        this.session = session;
        this.reviewDataImporterFactory = reviewDataImporterFactory;
    }

    @GetMapping("/api/repositories/{owner}/{repo}/pulls/{number}/review")
    public PullRequestReviewResponse review(@PathVariable String owner, @PathVariable String repo, @PathVariable int number) {
        String token = session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        ImportedReviewData data = reviewDataImporterFactory.apply(token).importReviewData(owner + "/" + repo, number);
        return PullRequestReviewResponse.from(data);
    }
}
