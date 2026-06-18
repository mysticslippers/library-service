package me.ifmo.backend.DTO.user.request;

import jakarta.validation.constraints.Size;

public record CancelUserBlockRequest(
        @Size(max = 1000)
        String reason
) {
}
