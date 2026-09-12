package com.athena.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds the environment needed for `git` to authenticate an HTTPS clone
 * with a GitHub Personal Access Token, without the token ever appearing on
 * any process's command line. A plain {@code https://<token>@github.com/...}
 * URL would put the token in the `git` subprocess's own argv — visible to
 * any local user via `ps` for the process's lifetime, defeating the point
 * of keeping it out of a command line in the first place. Instead, `git`'s
 * {@code GIT_ASKPASS} mechanism invokes a small script that answers the
 * credential prompts by reading an environment variable, which isn't
 * visible in argv.
 */
public final class GitAskpass {

    private static final String TOKEN_ENV_VAR = "ATHENA_GIT_TOKEN";

    private GitAskpass() {
    }

    /** Environment variables to pass to the `git` subprocess to authenticate as {@code token}. */
    public static Map<String, String> environmentFor(String token) {
        Path script;
        try {
            script = Files.createTempFile("athena-askpass-", ".sh");
            // GitHub's own documented HTTPS-with-PAT convention: any non-empty username works
            // (they use "x-access-token"), and the PAT itself is the password.
            Files.writeString(script, "#!/bin/sh\n"
                    + "case \"$1\" in\n"
                    + "  Username*) echo \"x-access-token\" ;;\n"
                    + "  *) echo \"$" + TOKEN_ENV_VAR + "\" ;;\n"
                    + "esac\n");
        } catch (IOException e) {
            throw new GitCheckoutException("Could not prepare the git credential helper script", e);
        }
        if (!script.toFile().setExecutable(true)) {
            throw new GitCheckoutException("Could not make the git credential helper script executable: " + script);
        }
        script.toFile().deleteOnExit();
        return Map.of("GIT_ASKPASS", script.toString(), TOKEN_ENV_VAR, token);
    }
}
