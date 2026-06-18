package me.ifmo.backend.DTO.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateUserBlockRequest(
        @NotNull
        Long userId,

        @NotBlank
        @Size(max = 2000)
        String reason,

        LocalDateTime expiresAt
) {
}
