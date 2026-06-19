package me.ifmo.backend.dto.catalog.response;

import me.ifmo.backend.dto.library.response.BranchShortResponse;
import me.ifmo.backend.entities.enums.CopyStatus;

public record MaterialCopyResponse(
        Long id,
        Long materialId,
        String materialTitle,
        BranchShortResponse branch,
        String inventoryNumber,
        CopyStatus status,
        String shelfLocation
) {
}
