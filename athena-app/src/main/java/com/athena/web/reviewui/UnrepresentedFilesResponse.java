package com.athena.web.reviewui;

import java.util.List;

/** The changed files no Change represents, with the totals a coverage indicator needs (ticket #260). */
public record UnrepresentedFilesResponse(int changedFileCount, int representedFileCount,
                                         List<UnrepresentedFileResponse> files) {
}
