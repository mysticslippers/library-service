package me.ifmo.backend.DTO.user.request;

import jakarta.validation.constraints.Size;

public record CancelUserWarningRequest(
        @Size(max = 1000)
        String reason
) {
}
