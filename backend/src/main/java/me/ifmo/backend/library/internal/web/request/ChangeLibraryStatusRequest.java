package me.ifmo.backend.library.internal.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.library.internal.domain.enums.LibraryStatus;

public record ChangeLibraryStatusRequest(
        @NotNull
        LibraryStatus status
) {
}
