package me.ifmo.backend.library.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateBranchRequest(
        @Size(max = 255)
        String name,

        @Valid
        BranchAddressRequest address
) {
}
