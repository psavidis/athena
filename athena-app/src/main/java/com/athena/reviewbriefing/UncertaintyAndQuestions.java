package com.athena.reviewbriefing;

import java.util.List;

/**
 * What an AI reasoning pass over a PR's focus areas (ticket #221)
 * couldn't confidently explain, and investigation questions worth
 * asking — the two are generated together from one pass rather than two
 * independent AI calls, since a question naturally investigates the
 * same uncertainty it's paired with. Either list may be empty: a
 * confidently-explainable PR is not forced to have a minimum count of
 * either (fabricating uncertainty would defeat this ticket's own
 * purpose).
 */
public record UncertaintyAndQuestions(List<BriefingItem> uncertainties, List<BriefingItem> questions) {

    public UncertaintyAndQuestions {
        uncertainties = List.copyOf(uncertainties);
        questions = List.copyOf(questions);
    }

    public static UncertaintyAndQuestions none() {
        return new UncertaintyAndQuestions(List.of(), List.of());
    }
}
