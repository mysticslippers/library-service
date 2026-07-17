package me.ifmo.backend.library.internal.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateBranchRequest(
        @Size(max = 255)
        String name,

        @Valid
        BranchAddressRequest address
) {
}
