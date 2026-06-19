package me.ifmo.backend.dto.library.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBranchRequest(
        @NotNull
        Long libraryId,

        @NotBlank
        @Size(max = 255)
        String name,

        @Valid
        @NotNull
        BranchAddressRequest address
) {
}
