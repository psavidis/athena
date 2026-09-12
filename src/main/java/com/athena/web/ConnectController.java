package com.athena.web;

import com.athena.github.GitHubClient;
import com.athena.github.GitHubConnectionResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Submits a GitHub Personal Access Token (ticket #73). The token itself is
 * never returned to the frontend and never persisted — only held in the
 * server-side session ({@link WebSession}) for the rest of this browser
 * session's calls.
 */
@RestController
public class ConnectController {

    private final WebSession session;

    public ConnectController(WebSession session) {
        this.session = session;
    }

    @PostMapping("/api/connect")
    public ResponseEntity<ConnectResponse> connect(@RequestBody ConnectRequest request) {
        GitHubConnectionResult result = new GitHubClient(request.token()).connect();
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ConnectResponse(null, result.errorMessage()));
        }
        session.connect(request.token());
        return ResponseEntity.ok(new ConnectResponse(result.authenticatedUsername(), null));
    }
}
