package me.ifmo.backend.library.web.request;

import jakarta.validation.constraints.NotNull;
import me.ifmo.backend.library.domain.enums.LibraryStatus;

public record ChangeLibraryStatusRequest(
        @NotNull
        LibraryStatus status
) {
}
