package com.athena.repository;

import java.util.Optional;

/**
 * A file changed by a Pull Request: its path, its status (added/modified/
 * removed/renamed, as reported by the provider), and its textual diff when
 * the provider makes one available (absent for e.g. binary files —
 * represented explicitly rather than as a silently empty diff).
 * Provider-independent.
 */
public record ChangedFile(String path, String status, Optional<String> diff) {
}
