package com.athena.web.github;

/** A reviewer's overall verdict on a Pull Request, serialized for the frontend (ticket #192). */
public record ReviewVerdictResponse(String reviewer, String state) {
}
