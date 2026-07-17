package me.ifmo.backend.user.web.request;

import jakarta.validation.constraints.Size;

public record CancelUserWarningRequest(
        @Size(max = 1000)
        String reason
) {
}
