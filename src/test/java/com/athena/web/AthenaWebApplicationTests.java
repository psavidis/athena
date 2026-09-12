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

    @Container
    static final GenericContainer<?> app = new GenericContainer<>(
            new ImageFromDockerfile().withDockerfile(Path.of(System.getProperty("user.dir"), "Dockerfile")))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health"));

    @Test
    void healthEndpointRespondsOk() throws Exception {
        URI uri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8080) + "/health");
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("OK");
    }

    // The two scenarios below deliberately cover only the auth-failure path: an invalid token
    // still crosses the real network to GitHub's real API and fails deterministically, no secret
    // needed. The success path (a valid token, real repos/PRs) would require provisioning a real
    // GitHub token as a CI secret — out of this ticket's scope; manually verified end to end this
    // session instead (see the PR description).

    @Test
    void connectingWithAnInvalidTokenIsRejected() throws Exception {
        URI uri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8080) + "/api/connect");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"token\":\"not-a-real-token\"}"))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void listingRepositoriesWithoutConnectingIsRejected() throws Exception {
        URI uri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8080) + "/api/repositories");
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
