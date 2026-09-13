package com.athena.web.ai;

/** Whether an AI provider is configured server-side, serialized for the frontend. */
public record AiStatusResponse(boolean configured) {
}
