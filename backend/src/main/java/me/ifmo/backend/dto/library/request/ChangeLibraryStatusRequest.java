package me.ifmo.backend.dto.library.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.entities.enums.LibraryStatus;

public record ChangeLibraryStatusRequest(
        @NotNull
        LibraryStatus status
) {
}
