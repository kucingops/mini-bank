package com.minibank.transaction.exception;

import org.springframework.http.HttpStatus;

/**
 * An Idempotency-Key was reused with a different request body. Mapped to 409 Conflict.
 */
public class IdempotencyConflictException extends ApiException {
    public IdempotencyConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
