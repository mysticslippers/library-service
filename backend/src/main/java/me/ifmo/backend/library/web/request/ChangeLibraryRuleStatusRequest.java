package me.ifmo.backend.library.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.library.domain.enums.LibraryRuleStatus;

public record ChangeLibraryRuleStatusRequest(
        @NotNull
        LibraryRuleStatus status
) {
}
