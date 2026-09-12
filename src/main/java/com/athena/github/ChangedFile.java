package com.athena.github;

import java.util.Optional;

/**
 * A file changed by a Pull Request: its path, its status (added/modified/
 * removed/renamed, as reported by GitHub), and its textual diff when GitHub
 * provides one (absent for e.g. binary files — represented explicitly
 * rather than as a silently empty diff).
 */
public record ChangedFile(String path, String status, Optional<String> diff) {
}
