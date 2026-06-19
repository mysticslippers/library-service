package me.ifmo.backend.dto.library.response;

import me.ifmo.backend.entities.enums.BranchStatus;

public record BranchResponse(
        Long id,
        LibraryShortResponse library,
        String name,
        BranchAddressResponse address,
        BranchStatus status
) {
}
