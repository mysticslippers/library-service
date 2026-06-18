package me.ifmo.backend.DTO.user.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.entities.enums.UserStatus;

public record ChangeUserStatusRequest(
        @NotNull
        UserStatus status,

        @Size(max = 1000)
        String reason
) {
}
