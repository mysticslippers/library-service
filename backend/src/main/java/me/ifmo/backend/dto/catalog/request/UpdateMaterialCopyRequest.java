package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.constraints.Size;
import me.ifmo.backend.entities.enums.CopyStatus;

public record UpdateMaterialCopyRequest(
        CopyStatus status,

        @Size(max = 100)
        String shelfLocation
) {
}
