package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMaterialCopyRequest(
        @NotNull
        Long materialId,

        @NotNull
        Long branchId,

        @NotBlank
        @Size(max = 100)
        String inventoryNumber,

        @Size(max = 100)
        String shelfLocation
) {
}
