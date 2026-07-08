package com.exploreceylon.backend.exception;

/**
 * Thrown when a verification send/verify is rejected by rate limiting
 * (cooldown, hourly cap, or an active abuse lockout). Mapped to HTTP 429 by
 * {@link GlobalExceptionHandler}.
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
