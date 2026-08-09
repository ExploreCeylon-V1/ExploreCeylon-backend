package com.exploreceylon.backend.exception;

public class DuplicateSubscriptionException extends RuntimeException {
    public DuplicateSubscriptionException(String message) {
        super(message);
    }
}
