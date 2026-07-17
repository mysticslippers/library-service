package me.ifmo.backend.dto.circulation.request;

import jakarta.validation.constraints.Size;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;

public record ReturnLoanRequest(
        CopyStatus resultingCopyStatus,

        @Size(max = 1000)
        String comment
) {
}
