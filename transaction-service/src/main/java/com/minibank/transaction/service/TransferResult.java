package com.minibank.transaction.service;

import com.minibank.transaction.dto.TransferResponse;

/**
 * Outcome of initiating a transfer.
 *
 * @param response the transfer
 * @param replayed true when an earlier transfer with the same Idempotency-Key was returned
 *                 instead of creating a new one
 */
public record TransferResult(TransferResponse response, boolean replayed) {
}
