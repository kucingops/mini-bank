package com.minibank.account.controller;

import com.minibank.account.dto.AccountResponse;
import com.minibank.account.dto.BalanceResponse;
import com.minibank.account.dto.CreateAccountRequest;
import com.minibank.account.dto.PageResponse;
import com.minibank.account.security.SecurityUtils;
import com.minibank.account.service.AccountService;
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
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Account", description = "Account management — create, retrieve, and query bank accounts")
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Create a new bank account (ADMIN)",
            description = "Creates a new account with holder name, email, initial balance, and daily transfer limit")
    @ApiResponse(responseCode = "201", description = "Account created")
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List all accounts (ADMIN)",
            description = "Paginated list of all registered bank accounts. Default sort: newest first")
    @GetMapping
    public ResponseEntity<PageResponse<AccountResponse>> getAllAccounts(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(accountService.getAllAccounts(pageable));
    }

    @Operation(summary = "Get account by ID",
            description = "Retrieves a single account. USER can only read their own account; ADMIN can read any")
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(
            @Parameter(description = "Account ID", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
            @PathVariable UUID id) {
        SecurityUtils.requireOwnerOrAdmin(id);
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @Operation(summary = "Get account balance",
            description = "Returns the balance (served from Redis cache when available). USER can only read their own balance")
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account ID", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
            @PathVariable UUID id) {
        SecurityUtils.requireOwnerOrAdmin(id);
        return ResponseEntity.ok(new BalanceResponse(id, accountService.getBalance(id)));
    }

    @Operation(summary = "Find account by account number",
            description = "Looks up an account using its unique account number. Owners and admins get full details; "
                    + "other users only get the public fields needed to address a transfer (id, number, holder name, status)")
    @GetMapping("/by-number/{accountNumber}")
    public ResponseEntity<AccountResponse> getByAccountNumber(
            @Parameter(description = "10-digit account number", example = "1234567890")
            @PathVariable String accountNumber) {
        AccountResponse account = accountService.getAccountByNumber(accountNumber);
        if (SecurityUtils.isAdmin() || SecurityUtils.isOwner(account.getId())) {
            return ResponseEntity.ok(account);
        }
        return ResponseEntity.ok(AccountResponse.publicView(account));
    }
}
