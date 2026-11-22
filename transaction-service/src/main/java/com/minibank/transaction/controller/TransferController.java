package com.minibank.transaction.controller;

import com.minibank.transaction.dto.PageResponse;
import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.dto.TransferResponse;
import com.minibank.transaction.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
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

    private final TransferService transferService;

    /**
     * Initiate a fund transfer.
     * Returns 202 Accepted — processing happens asynchronously via Redis Streams.
     */
    @Operation(summary = "Initiate a fund transfer", description = "Submits a transfer from the caller's own account. Returns 202 Accepted with status PENDING — the fraud check and the debit/credit happen asynchronously via Redis Streams; poll GET /api/v1/transfers/{id} for the result")
    @ApiResponse(responseCode = "202", description = "Accepted; status is PENDING until the fraud check completes")
    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.initiateTransfer(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
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
