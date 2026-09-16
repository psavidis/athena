package com.athena.web.livesession;

/** Request body to start a Live Code Review Session (ticket #158) from the caller's currently selected PR. */
public record CreateLiveSessionRequest(String displayName) {
}
