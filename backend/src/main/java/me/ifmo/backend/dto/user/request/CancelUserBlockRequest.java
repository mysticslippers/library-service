package me.ifmo.backend.dto.user.request;

import jakarta.validation.constraints.Size;

public record CancelUserBlockRequest(
        @Size(max = 1000)
        String reason
) {
}
