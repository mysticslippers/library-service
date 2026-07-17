package me.ifmo.backend.library.internal.web.response;

import me.ifmo.backend.library.internal.domain.enums.BranchStatus;

public record BranchShortResponse(
        Long id,
        Long libraryId,
        String name,
        BranchAddressResponse address,
        BranchStatus status
) {
}
