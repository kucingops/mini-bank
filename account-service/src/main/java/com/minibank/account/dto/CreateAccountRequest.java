package com.minibank.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Admin account creation")
public class CreateAccountRequest {

    @Schema(description = "Account holder's full name", example = "Budi Santoso")
    @NotBlank(message = "Holder name is required")
    @Size(max = 100, message = "Holder name max 100 characters")
    private String holderName;

    @Schema(description = "Contact email", example = "budi.new@minibank.com")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(description = "Opening balance in IDR (default 0)", example = "100000000")
    @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
    private BigDecimal initialBalance;

    @Schema(description = "Daily outgoing transfer limit in IDR (min 1,000,000)", example = "50000000")
    @DecimalMin(value = "1000000.00", message = "Daily transfer limit minimum Rp 1,000,000")
    private BigDecimal dailyTransferLimit;
}
