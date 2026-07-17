package me.ifmo.backend.library.internal.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.library.internal.domain.enums.LibraryRuleStatus;

public record ChangeLibraryRuleStatusRequest(
        @NotNull
        LibraryRuleStatus status
) {
}
