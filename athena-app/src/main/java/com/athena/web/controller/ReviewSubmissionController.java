package com.athena.web.controller;

import com.athena.github.CommentSyncer;
import com.athena.github.GitHubTransport;
import com.athena.github.HttpGitHubTransport;
import com.athena.github.ReviewDecisionSyncer;
import com.athena.reviewcontext.PreSubmissionSummary;
import com.athena.reviewcontext.ReviewAnnotationSync;
import com.athena.reviewcontext.ReviewAnnotationSyncResult;
import com.athena.reviewcontext.ReviewContext;
import com.athena.semantic.Change;
import com.athena.web.ChangeKey;
import com.athena.web.request.SetReviewStateRequest;
import com.athena.web.WebSession;
import com.athena.web.response.PreSubmissionSummaryResponse;
import com.athena.web.response.SubmissionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Setting a Change's review state, the pre-submission summary, and
 * confirmed submission of comments + review decision to GitHub (ticket
 * #76). Reuses {@link com.athena.semantic.ReviewStateStore},
 * {@link PreSubmissionSummary}, {@link com.athena.reviewcontext.ReviewSubmission},
 * {@link ReviewContext}, {@link CommentSyncer}, {@link ReviewDecisionSyncer}
 * unmodified.
 */
@RestController
public class ReviewSubmissionController {

    private final WebSession session;
    private final GitHubTransport transport;

    @Autowired
    public ReviewSubmissionController(WebSession session) {
        this(session, new HttpGitHubTransport(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()));
    }

    /** Test seam: a fake {@link GitHubTransport} stands in for the real network boundary. */
    ReviewSubmissionController(WebSession session, GitHubTransport transport) {
        this.session = session;
        this.transport = transport;
    }

    @PatchMapping("/api/review/changes/{changeKey}/review-state")
    public void setReviewState(@PathVariable String changeKey, @RequestBody SetReviewStateRequest request) {
        WebSession.SelectedPullRequest selection = requireSelection();
        Change change = requireChange(changeKey, selection.changes());
        selection.reviewStateStore().setState(change, request.state());
    }

    @GetMapping("/api/review/pre-submission-summary")
    public PreSubmissionSummaryResponse preSubmissionSummary() {
        WebSession.SelectedPullRequest selection = requireSelection();
        PreSubmissionSummary summary = PreSubmissionSummary.of(reviewContext(selection));
        return PreSubmissionSummaryResponse.of(summary);
    }

    @PostMapping("/api/review/submit")
    public SubmissionResponse submit() {
        WebSession.SelectedPullRequest selection = requireSelection();
        if (selection.reviewSubmission().isSubmitted()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Review already submitted");
        }

        ReviewContext context = reviewContext(selection);
        PreSubmissionSummary summary = PreSubmissionSummary.of(context);
        String token = session.gitHubToken().orElseThrow();

        ReviewAnnotationSync annotationSync = new ReviewAnnotationSync(new CommentSyncer(token, transport));
        ReviewAnnotationSyncResult syncResult = annotationSync.syncComments(
                selection.annotationBoard(), selection.repositoryFullName(), selection.pullRequest().number());

        ReviewDecisionSyncer decisionSyncer = new ReviewDecisionSyncer(token, transport);
        String decisionBody = "Submitted via Athena.";
        if (summary.gitHubAction() == PreSubmissionSummary.GitHubAction.APPROVE) {
            decisionSyncer.syncApproval(selection.repositoryFullName(), selection.pullRequest().number(), decisionBody);
        } else {
            decisionSyncer.syncRequestChanges(selection.repositoryFullName(), selection.pullRequest().number(), decisionBody);
        }

        selection.reviewSubmission().confirmAndSubmit();
        return new SubmissionResponse(syncResult.isFullySuccessful(), syncResult.succeeded().size(),
                syncResult.failed().size());
    }

    private ReviewContext reviewContext(WebSession.SelectedPullRequest selection) {
        return ReviewContext.assemble(selection.pullRequest().title(), selection.changes(), selection.reviewStateStore(),
                selection.annotationBoard());
    }

    private WebSession.SelectedPullRequest requireSelection() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));
    }

    private Change requireChange(String changeKey, List<Change> changes) {
        return ChangeKey.resolve(changeKey, changes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
    }
}
