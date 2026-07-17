package me.ifmo.backend.library.web.response;

import me.ifmo.backend.library.domain.enums.BranchStatus;

public record BranchResponse(
        Long id,
        LibraryShortResponse library,
        String name,
        BranchAddressResponse address,
        BranchStatus status
) {
}
