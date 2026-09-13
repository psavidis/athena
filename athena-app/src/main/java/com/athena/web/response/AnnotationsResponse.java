package com.athena.web.response;

import java.util.List;

/** Comments and private notes at one scope, serialized for the frontend (ticket #75). */
public record AnnotationsResponse(List<String> comments, List<String> privateNotes) {
}
