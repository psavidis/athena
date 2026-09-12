package com.athena.web;

import com.athena.ai.AiFindingDisposition;

/**
 * One AI-surfaced finding, serialized for the frontend (ticket #77).
 * {@code jumpTargetChangeKey} is {@code null} for a general finding not
 * tied to one specific Change — external DTOs allow nullable fields
 * (CODE_STYLE.md §D.1).
 */
public record AiFindingResponse(String id, String description, AiFindingDisposition disposition,
                                 String jumpTargetChangeKey) {
}
