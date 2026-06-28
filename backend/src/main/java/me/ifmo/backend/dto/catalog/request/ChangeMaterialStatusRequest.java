package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.MaterialStatus;

public record ChangeMaterialStatusRequest(
        @NotNull
        MaterialStatus status
){
}
