package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.constraints.Size;
import me.ifmo.backend.entities.enums.CopyStatus;

public record UpdateMaterialCopyRequest(

        @Size(max = 100)
        String shelfLocation
) {
}
