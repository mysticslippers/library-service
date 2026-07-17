package me.ifmo.backend.library.web.response;

import me.ifmo.backend.library.domain.enums.LibraryStatus;

public record LibraryShortResponse(
        Long id,
        String code,
        String name,
        LibraryStatus status
) {
}
