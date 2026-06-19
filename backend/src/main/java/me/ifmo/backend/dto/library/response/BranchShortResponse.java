package me.ifmo.backend.dto.library.response;

import me.ifmo.backend.entities.enums.BranchStatus;

public record BranchShortResponse(
        Long id,
        Long libraryId,
        String name,
        BranchAddressResponse address,
        BranchStatus status
) {
}
