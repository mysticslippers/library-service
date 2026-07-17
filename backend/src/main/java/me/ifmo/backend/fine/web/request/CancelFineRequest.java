package me.ifmo.backend.fine.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelFineRequest(
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
