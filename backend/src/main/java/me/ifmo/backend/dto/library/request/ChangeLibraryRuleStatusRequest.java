package me.ifmo.backend.dto.library.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.LibraryRuleStatus;

public record ChangeLibraryRuleStatusRequest(
        @NotNull
        LibraryRuleStatus status
) {
}
