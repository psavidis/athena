package com.athena.web;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds the application's real Docker image and starts it as an actual
 * container — blackbox, over the network, the same artifact a user would
 * run (CODE_STYLE.md F.6). No {@code @SpringBootTest}: this never touches
 * an in-process Spring context.
 */
@Testcontainers
class AthenaWebApplicationTests {

    // The Dockerfile builds the whole multi-module project (see its own comments) and lives at
    // the repository root, one level above this module's own working directory — not
    // System.getProperty("user.dir") itself, which Maven sets to athena-app/.
    @Container
    static final GenericContainer<?> app = new GenericContainer<>(
            new ImageFromDockerfile().withDockerfile(
                    Path.of(System.getProperty("user.dir")).getParent().resolve("Dockerfile")))
            .withExposedPorts(7332)
            .waitingFor(Wait.forHttp("/health"));

    @Test
    void healthEndpointRespondsOk() throws Exception {
        URI uri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(7332) + "/health");
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("OK");
    }

    // GitHub App installation is now the connect mechanism (replacing the earlier Personal
    // Access Token entry): a fresh container has no App registered yet, so /api/github/status
    // deterministically reports "not connected" with no GitHub network call and no secret needed.
    // The full installation flow (App manifest creation, install, callbacks) requires a real
    // browser round-trip through github.com and was manually verified end to end instead (see
    // the PR description).

    @Test
    void aFreshContainerReportsGitHubNotConnected() throws Exception {
        URI uri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(7332) + "/api/github/status");
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"connected\":false");
    }

    @Test
    void listingRepositoriesWithoutConnectingIsRejected() throws Exception {
        URI uri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(7332) + "/api/repositories");
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void requestingTheChangeMapWithoutConnectingIsRejected() throws Exception {
        URI uri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(7332) + "/api/review/change-map");
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
