package com.athena.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Installs a real, pinned ESLint CLI into a project root for tests (ticket #114/#117)
 * — never mocked, per CODE_STYLE.md/qa-ticket's Detroit-school rule: running the real
 * ESLint CLI as a subprocess is exactly the kind of genuine, fast, real collaborator
 * PmdAnalysisProviderTest already exercises in-process for PMD (ticket #116); this is
 * the same idea for a tool that happens to run out-of-process. Pinned to a specific
 * major version deliberately (mirrors the parent pom's {@code ${pmd.version}} pin for
 * ticket #116) so a future ESLint major's own breaking changes (its flat-config model,
 * its {@code --format json} shape) can't silently change test behavior between runs —
 * see {@link com.athena.plugins.ESLintAnalysisProvider}'s own Javadoc for why the
 * provider itself does not hardcode a version.
 */
final class EslintTestFixture {

    /** Pinned deliberately — see class Javadoc. The current ESLint major line as of ticket #117. */
    private static final String ESLINT_PACKAGE_SPEC = "eslint@9";

    private static final String FLAT_CONFIG_ENABLING_NO_UNUSED_VARS = "module.exports = [\n"
            + "  {\n"
            + "    rules: {\n"
            + "      \"no-unused-vars\": \"error\"\n"
            + "    }\n"
            + "  }\n"
            + "];\n";

    private EslintTestFixture() {
    }

    /**
     * Installs a real ESLint into {@code projectRoot}/node_modules (via a real,
     * network-touching {@code npm install}, exactly as a real analyzed project's own
     * ESLint would already be installed) and writes a minimal flat config enabling one
     * rule ({@code no-unused-vars}) — enough for a real, deterministic violation.
     */
    static void installInto(Path projectRoot) throws IOException, InterruptedException {
        runNpmInstall(projectRoot);
        Files.writeString(projectRoot.resolve("eslint.config.js"), FLAT_CONFIG_ENABLING_NO_UNUSED_VARS);
    }

    private static void runNpmInstall(Path projectRoot) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "npm", "install", "--no-save", "--prefix", projectRoot.toString(), ESLINT_PACKAGE_SPEC);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(2, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("timed out installing a real ESLint for testing");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("could not install a real ESLint for testing: " + output);
        }
    }
}
