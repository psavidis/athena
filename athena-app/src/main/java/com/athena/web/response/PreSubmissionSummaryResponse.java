package com.athena.web.response;

import com.athena.reviewcontext.PreSubmissionSummary;

import java.util.List;

/** The pre-submission summary, serialized for the frontend (ticket #76). */
public record PreSubmissionSummaryResponse(List<String> reviewedChangeTitles, List<String> mechanicalChangeTitles,
                                            List<String> concernChangeTitles, int commentCount,
                                            PreSubmissionSummary.GitHubAction gitHubAction) {

    static PreSubmissionSummaryResponse of(PreSubmissionSummary summary) {
        return new PreSubmissionSummaryResponse(summary.reviewedChangeTitles(), summary.mechanicalChangeTitles(),
                summary.concernChangeTitles(), summary.commentCount(), summary.gitHubAction());
    }
}
