package me.ifmo.backend.dto.library.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateBranchRequest(
        @Size(max = 255)
        String name,

        @Valid
        BranchAddressRequest address
) {
}
