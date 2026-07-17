package me.ifmo.backend.library.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.library.domain.enums.BranchStatus;

public record ChangeBranchStatusRequest(
        @NotNull
        BranchStatus status
){
}
