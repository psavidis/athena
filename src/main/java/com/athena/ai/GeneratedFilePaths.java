package com.athena.ai;

import java.util.Locale;

/**
 * Conservative, path-based heuristic for whether a file is generated code
 * (epic #7 §36's "Generated files" exclusion). This repo's Change model
 * (epic #4) is deliberately Java-only MVP with no build-tool/annotation
 * awareness, so there is no existing "generated" marker to key off of —
 * this looks at conventional path segments instead of file content.
 */
final class GeneratedFilePaths {

    private GeneratedFilePaths() {
    }

    static boolean isGenerated(String filePath) {
        String normalized = filePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.startsWith("generated/") || normalized.contains("/generated/");
    }
}
