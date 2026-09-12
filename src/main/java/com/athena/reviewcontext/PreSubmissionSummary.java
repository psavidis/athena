package com.athena.reviewcontext;

import com.athena.semantic.Change;

import java.util.List;

/**
 * The summary a reviewer sees before submitting a review (epic #6 §32):
 * what was reviewed, what was classified mechanical, concerns, and the
 * comment count. Submission itself is a separate, explicitly-confirmed
 * action ({@link ReviewSubmission}) — this class only presents the summary.
 */
public final class PreSubmissionSummary {

    private final List<String> reviewedChangeTitles;
    private final List<String> mechanicalChangeTitles;
    private final List<String> concernChangeTitles;
    private final int commentCount;

    private PreSubmissionSummary(List<String> reviewedChangeTitles, List<String> mechanicalChangeTitles,
                                  List<String> concernChangeTitles, int commentCount) {
        this.reviewedChangeTitles = List.copyOf(reviewedChangeTitles);
        this.mechanicalChangeTitles = List.copyOf(mechanicalChangeTitles);
        this.concernChangeTitles = List.copyOf(concernChangeTitles);
        this.commentCount = commentCount;
    }

    public static PreSubmissionSummary of(ReviewContext context) {
        return new PreSubmissionSummary(
                titlesOf(context.reviewedChanges()),
                titlesOf(context.mechanicalChanges()),
                titlesOf(context.concernChanges()),
                context.comments().size());
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

    public int commentCount() {
        return commentCount;
    }
}
