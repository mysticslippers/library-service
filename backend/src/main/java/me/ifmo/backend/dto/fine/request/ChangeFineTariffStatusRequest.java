package me.ifmo.backend.dto.fine.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.FineTariffStatus;

public record ChangeFineTariffStatusRequest(
        @NotNull
        FineTariffStatus status
) {
}
