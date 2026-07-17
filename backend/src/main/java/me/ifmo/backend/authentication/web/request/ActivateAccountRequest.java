package me.ifmo.backend.authentication.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateAccountRequest(
        @NotBlank
        @Size(max = 512)
        String token
) {
}
