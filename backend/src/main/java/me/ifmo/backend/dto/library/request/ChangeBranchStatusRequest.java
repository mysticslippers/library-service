package me.ifmo.backend.dto.library.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.BranchStatus;

public record ChangeBranchStatusRequest(
        @NotNull
        BranchStatus status
){
}
