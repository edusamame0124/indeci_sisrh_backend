package com.indeci.exception;

/**
 * Demasiados intentos de login (rate-limit). Debe mapearse a HTTP 429.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
