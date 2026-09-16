package com.athena.web.reviewreplay;

/**
 * Request body to import a Review Recording artifact a teammate exported
 * from their own Athena instance (ticket #217): the local filesystem path
 * to the shared artifact file — file-based/manual sharing only, no
 * centralized Athena service.
 */
public record ImportSharedArtifactRequest(String filePath) {
}
