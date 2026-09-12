package com.athena.reviewcontext;

import com.athena.reviewui.Comment;
import com.athena.semantic.Change;

import java.util.List;

/**
 * The summary a reviewer sees before submitting a review (epic #6 §32):
 * what was reviewed, what was classified mechanical, concerns, the comment
 * count, and the GitHub action submission will result in — so the reviewer
 * has the sign-off information the ticket's own user story promises, not
 * just Athena-internal state. Submission itself is a separate, explicitly-
 * confirmed action ({@link ReviewSubmission}) — this class only presents
 * the summary.
 */
public final class PreSubmissionSummary {

    /** The GitHub review decision submission will result in (§32's "GitHub actions" line). */
    public enum GitHubAction {
        /** No open concerns — the review approves the PR. */
        APPROVE,
        /** At least one open concern — the review requests changes. */
        REQUEST_CHANGES
    }

    private final List<String> reviewedChangeTitles;
    private final List<String> mechanicalChangeTitles;
    private final List<String> concernChangeTitles;
    private final int lineCommentCount;
    private final int generalCommentCount;
    private final GitHubAction gitHubAction;

    private PreSubmissionSummary(List<String> reviewedChangeTitles, List<String> mechanicalChangeTitles,
                                  List<String> concernChangeTitles, int lineCommentCount, int generalCommentCount,
                                  GitHubAction gitHubAction) {
        this.reviewedChangeTitles = List.copyOf(reviewedChangeTitles);
        this.mechanicalChangeTitles = List.copyOf(mechanicalChangeTitles);
        this.concernChangeTitles = List.copyOf(concernChangeTitles);
        this.lineCommentCount = lineCommentCount;
        this.generalCommentCount = generalCommentCount;
        this.gitHubAction = gitHubAction;
    }

    public static PreSubmissionSummary of(ReviewContext context) {
        List<Comment> comments = context.comments();
        long lineComments = comments.stream().filter(c -> c.scope().lineLocation().isPresent()).count();
        long generalComments = comments.size() - lineComments;
        GitHubAction action = context.concernChanges().isEmpty() ? GitHubAction.APPROVE : GitHubAction.REQUEST_CHANGES;

        return new PreSubmissionSummary(
                titlesOf(context.reviewedChanges()),
                titlesOf(context.mechanicalChanges()),
                titlesOf(context.concernChanges()),
                (int) lineComments,
                (int) generalComments,
                action);
    }

    private static List<String> titlesOf(List<Change> changes) {
        return changes.stream().map(Change::title).toList();
    }

    public List<String> reviewedChangeTitles() {
        return reviewedChangeTitles;
    }

    public List<String> mechanicalChangeTitles() {
        return mechanicalChangeTitles;
    }

    public List<String> concernChangeTitles() {
        return concernChangeTitles;
    }

    public int lineCommentCount() {
        return lineCommentCount;
    }

    public int generalCommentCount() {
        return generalCommentCount;
    }

    public int commentCount() {
        return lineCommentCount + generalCommentCount;
    }

    /** The GitHub review decision submission will result in — request-changes if any concern is open. */
    public GitHubAction gitHubAction() {
        return gitHubAction;
    }
}
