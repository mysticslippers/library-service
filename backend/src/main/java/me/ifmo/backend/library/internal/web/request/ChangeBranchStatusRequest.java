package me.ifmo.backend.library.internal.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.library.internal.domain.enums.BranchStatus;

public record ChangeBranchStatusRequest(
        @NotNull
        BranchStatus status
){
}
