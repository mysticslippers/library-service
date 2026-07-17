package me.ifmo.backend.dto.catalog.response;

import me.ifmo.backend.library.web.response.BranchShortResponse;
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
