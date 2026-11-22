package com.minibank.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Fund transfer request")
public class TransferRequest {

    @Schema(description = "Source account — must be the caller's own account", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
    @NotNull(message = "Source account ID is required")
    private UUID fromAccountId;

    @Schema(description = "Destination account", example = "9b2e4f6a-1c3d-4e5f-8a7b-6c5d4e3f2a1b")
    @NotNull(message = "Destination account ID is required")
    private UUID toAccountId;

    @Schema(description = "Amount in IDR (min 10,000)", example = "1000000")
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10000.00", message = "Minimum transfer amount is Rp 10,000")
    private BigDecimal amount;

    @Schema(description = "Free-text note (max 255)", example = "Bayar makan siang")
    @Size(max = 255, message = "Description max 255 characters")
    private String description;
}
