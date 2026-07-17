package me.ifmo.backend.library.internal.web.response;

import me.ifmo.backend.library.internal.domain.enums.BranchStatus;

public record BranchResponse(
        Long id,
        LibraryShortResponse library,
        String name,
        BranchAddressResponse address,
        BranchStatus status
) {
}
