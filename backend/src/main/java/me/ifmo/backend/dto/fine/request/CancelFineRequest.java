package me.ifmo.backend.dto.fine.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelFineRequest(
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
