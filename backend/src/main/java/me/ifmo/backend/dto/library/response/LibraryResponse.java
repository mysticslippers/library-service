package me.ifmo.backend.dto.library.response;

import me.ifmo.backend.entities.enums.LibraryStatus;

public record LibraryResponse(
        Long id,
        String code,
        String name,
        LibraryStatus status
) {
}
