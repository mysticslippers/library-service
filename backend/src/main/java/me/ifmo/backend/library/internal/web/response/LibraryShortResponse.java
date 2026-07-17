package me.ifmo.backend.library.internal.web.response;

import me.ifmo.backend.library.internal.domain.enums.LibraryStatus;

public record LibraryShortResponse(
        Long id,
        String code,
        String name,
        LibraryStatus status
) {
}
