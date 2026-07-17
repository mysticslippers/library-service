package me.ifmo.backend.fine.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.fine.domain.enums.FineTariffStatus;

public record ChangeFineTariffStatusRequest(
        @NotNull
        FineTariffStatus status
) {
}
