package me.ifmo.backend.user.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.user.domain.enums.UserStatus;

public record ChangeUserStatusRequest(
        @NotNull
        UserStatus status,

        @Size(max = 1000)
        String reason
) {
}
