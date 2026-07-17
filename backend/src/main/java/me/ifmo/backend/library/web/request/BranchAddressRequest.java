package me.ifmo.backend.library.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchAddressRequest(
        @NotBlank
        @Size(max = 100)
        String city,

        @NotBlank
        @Size(max = 255)
        String street,

        @NotBlank
        @Size(max = 50)
        String building
) {
}
