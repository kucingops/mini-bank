package com.minibank.transaction.controller;

import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.dto.TransferResponse;
import com.minibank.transaction.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfer", description = "Fund transfer operations — initiate, track, and view transaction history")
public class TransferController {

    private final TransferService transferService;

    /**
     * Initiate a fund transfer.
     * Returns 202 Accepted — processing happens asynchronously via Redis Streams.
     */
    @Operation(summary = "Initiate a fund transfer", description = "Submits a transfer request. Returns 202 Accepted — processing happens asynchronously via Redis Streams with fraud detection")
    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.initiateTransfer(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Get transfer status", description = "Retrieves the current status of a transfer by its transaction ID (PENDING, COMPLETED, REJECTED)")
    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }

    @Operation(summary = "Get transaction history", description = "Returns all transfers (sent and received) for a given account ID")
    @GetMapping("/history")
    public ResponseEntity<List<TransferResponse>> getHistory(@RequestParam UUID accountId) {
        return ResponseEntity.ok(transferService.getTransactionHistory(accountId));
    }
}
