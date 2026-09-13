package com.athena.web;

import com.athena.semantic.ChangeCategory;
import com.athena.semantic.TransformationKind;

import java.util.Set;

/** A Change's detail view, serialized for the frontend (ticket #75). */
public record ChangeDetailResponse(String changeKey, ChangeCategory category, TransformationKind kind,
                                    String description, Set<String> symbols, Set<String> files, String diff) {
}
