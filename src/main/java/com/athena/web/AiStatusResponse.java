package com.athena.web;

/** Whether an AI provider is configured server-side, serialized for the frontend. */
public record AiStatusResponse(boolean configured) {
}
