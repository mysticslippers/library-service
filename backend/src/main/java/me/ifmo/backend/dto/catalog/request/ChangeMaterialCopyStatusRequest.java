package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.CopyStatus;

public record ChangeMaterialCopyStatusRequest(
        @NotNull
        CopyStatus status
) {
}
