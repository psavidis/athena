package com.athena.web.ai;

import java.util.List;

/**
 * One module's "what changed and why" summary, serialized for the frontend:
 * which module, which Changes it covers (by changeKey, so the frontend can
 * link down into the Change Map), and the AI-generated narrative text.
 */
public record ModuleNarrativeResponse(String moduleName, List<String> changeKeys, String narrative) {
}
