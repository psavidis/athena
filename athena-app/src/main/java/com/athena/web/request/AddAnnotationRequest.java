package com.athena.web.request;

/** Request body for attaching a comment or private note at a scope (ticket #75). */
public record AddAnnotationRequest(AnnotationScopeRequest scope, String text) {
}
