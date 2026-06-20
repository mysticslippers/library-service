package me.ifmo.backend.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank
        @Size(max = 512)
        String token,

        @NotBlank
        @Size(min = 8, max = 255)
        String newPassword,

        @NotBlank
        @Size(min = 8, max = 255)
        String newPasswordConfirmation
) {
}
