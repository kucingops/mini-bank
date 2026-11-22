package com.minibank.transaction.controller;

import com.minibank.transaction.dto.ErrorResponse;
import com.minibank.transaction.dto.PageResponse;
import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.dto.TransferResponse;
import com.minibank.transaction.service.TransferResult;
import com.minibank.transaction.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfer", description = "Fund transfer operations — initiate, track, and view transaction history")
public class TransferController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final TransferService transferService;

    /**
     * Initiate a fund transfer.
     * Returns 202 Accepted — processing happens asynchronously via Redis Streams.
     */
    @Operation(summary = "Initiate a fund transfer", description = "Submits a transfer from the caller's own account. Returns 202 Accepted with status PENDING — the fraud check and the debit/credit happen asynchronously via Redis Streams; poll GET /api/v1/transfers/{id} for the result")
    @ApiResponse(responseCode = "202", description = "Accepted; status is PENDING until the fraud check completes. "
            + "On an idempotent replay the original transfer is returned with header Idempotent-Replayed: true",
            headers = @Header(name = "Idempotent-Replayed", description = "Present (true) when the response is a replay",
                    schema = @Schema(type = "string", example = "true")))
    @PostMapping
    @ApiResponse(responseCode = "409",
            description = "Idempotency-Key was already used for a different transfer request",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TransferResponse> initiateTransfer(
            @Parameter(in = ParameterIn.HEADER, name = IDEMPOTENCY_KEY,
                    description = "Optional but recommended. A unique value per transfer (e.g. a UUID, max 64 chars), "
                            + "scoped to the sender account. Retrying with the same key and the same body returns the "
                            + "original transfer (with `Idempotent-Replayed: true`) instead of creating a new one; "
                            + "reusing it with a different body returns 409.",
                    example = "8e3f5c2a-6b1d-4f7e-9a0c-2d4b6e8f1a3c")
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferResult result = transferService.initiateTransfer(request, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.ACCEPTED);
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(result.response());
    }

    @Operation(summary = "Get transfer status",
            description = "Current status of a transfer (PENDING, COMPLETED, REJECTED, FAILED). Only the sender, the recipient or an ADMIN can read it")
    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(
            @Parameter(description = "Transaction ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }

    @Operation(summary = "Get transaction history",
            description = "Paginated transfers (sent and received) for an account. USER can only read their own account. Default sort: newest first")
    @GetMapping("/history")
    public ResponseEntity<PageResponse<TransferResponse>> getHistory(
            @Parameter(description = "Account ID", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
            @RequestParam UUID accountId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(transferService.getTransactionHistory(accountId, pageable));
    }
}
