package me.ifmo.backend.fine.web.request;

import me.ifmo.backend.fine.domain.enums.FineStatus;
import me.ifmo.backend.fine.domain.enums.ViolationType;

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
