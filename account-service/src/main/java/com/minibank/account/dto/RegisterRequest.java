package com.minibank.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Self-registration. The account starts with balance 0 and the default daily limit")
public class RegisterRequest {

    @Schema(description = "Account holder's full name", example = "Ahmad Syarif")
    @NotBlank(message = "Holder name is required")
    @Size(max = 100, message = "Holder name max 100 characters")
    private String holderName;

    @Schema(description = "Login email, must be unique", example = "syarif.new@minibank.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(description = "Password, at least 6 characters", example = "password123", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
