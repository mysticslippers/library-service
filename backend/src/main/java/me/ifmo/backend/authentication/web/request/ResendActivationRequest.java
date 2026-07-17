package me.ifmo.backend.authentication.web.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendActivationRequest(
        @Email
        @NotBlank
        @Size(max = 255)
        String email
) {
}
