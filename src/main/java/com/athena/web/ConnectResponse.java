package com.athena.web;

/** Response body for {@code POST /api/connect}. Exactly one of the two fields is present. */
record ConnectResponse(String authenticatedUsername, String error) {
}
