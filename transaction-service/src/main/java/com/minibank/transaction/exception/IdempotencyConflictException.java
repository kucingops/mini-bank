package com.minibank.transaction.exception;

/**
 * An Idempotency-Key was reused with a different request body. Mapped to 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
