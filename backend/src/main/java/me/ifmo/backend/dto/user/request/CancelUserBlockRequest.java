package me.ifmo.backend.dto.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelUserBlockRequest(
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
