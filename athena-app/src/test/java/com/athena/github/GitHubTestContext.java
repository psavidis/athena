package com.athena.github;

/**
 * Shared Cucumber test state (token + fake transport) across the GitHub
 * step-definition classes, injected by Cucumber's default PicoContainer
 * dependency injection so multiple step classes can share one scenario's
 * state without duplicating step definitions.
 */
public class GitHubTestContext {

    // Fake GitHub HTTP boundary: real network calls to api.github.com are an
    // external system boundary tests can't/shouldn't cross for real.
    final FakeGitHubTransport transport = new FakeGitHubTransport();

    String token;
}
