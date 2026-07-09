package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.constraints.Size;

public record UpdateMaterialCopyRequest(
        Long branchId,

        @Size(max = 100)
        String inventoryNumber,

        @Size(max = 100)
        String shelfLocation
) {
}
