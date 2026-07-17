package me.ifmo.backend.library.web.response;

import me.ifmo.backend.library.domain.enums.BranchStatus;

public record BranchShortResponse(
        Long id,
        Long libraryId,
        String name,
        BranchAddressResponse address,
        BranchStatus status
) {
}
