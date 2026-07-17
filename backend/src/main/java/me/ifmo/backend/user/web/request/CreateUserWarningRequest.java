package me.ifmo.backend.user.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateUserWarningRequest(
        @NotNull
        Long userId,

        @NotBlank
        @Size(max = 2000)
        String reason,

        @Size(max = 2000)
        String comment,

        LocalDateTime expiresAt
) {
}
