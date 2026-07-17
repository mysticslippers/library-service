package me.ifmo.backend.catalog.web.request;

import jakarta.validation.constraints.Size;

public record UpdateMaterialCopyRequest(
        Long branchId,

        @Size(max = 100)
        String inventoryNumber,

        @Size(max = 100)
        String shelfLocation
) {
}
