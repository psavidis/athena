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
}
