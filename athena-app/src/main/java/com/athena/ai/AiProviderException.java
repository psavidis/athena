package com.athena.ai;

/**
 * Raised when an {@link AiProvider} cannot complete an analysis request —
 * the provider's API is unreachable, rejects the request, or returns a
 * response that can't be interpreted as findings.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
