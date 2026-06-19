package me.ifmo.backend.dto.fine.request;

import me.ifmo.backend.entities.enums.FineStatus;
import me.ifmo.backend.entities.enums.ViolationType;

import java.time.LocalDateTime;

public record FineSearchRequest(
        Long userId,
        Long loanId,
        Long copyId,
        ViolationType reason,
        FineStatus status,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
) {
}
