package me.ifmo.backend.catalog.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.catalog.domain.enums.MaterialStatus;

public record ChangeMaterialStatusRequest(
        @NotNull
        MaterialStatus status
){
}
