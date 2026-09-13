package com.athena.web.ai;

import java.util.List;

/** The AI context boundary a reviewer can inspect before triggering analysis, serialized for the frontend (ticket #77). */
public record AiContextBoundaryResponse(List<String> includedChangeTitles, List<String> excludedUnreviewedChangeTitles,
                                         List<String> excludedGeneratedChangeTitles, boolean privateNotesExcluded) {
}
