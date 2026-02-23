package com.minibank.account.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountRequest {

    @NotBlank(message = "Holder name is required")
    @Size(max = 100, message = "Holder name max 100 characters")
    private String holderName;

    @Email(message = "Invalid email format")
    private String email;

    @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
    private BigDecimal initialBalance;

    @DecimalMin(value = "1000000.00", message = "Daily transfer limit minimum Rp 1,000,000")
    private BigDecimal dailyTransferLimit;
}
