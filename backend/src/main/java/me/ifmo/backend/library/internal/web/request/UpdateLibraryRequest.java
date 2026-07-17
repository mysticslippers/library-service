package me.ifmo.backend.library.internal.web.request;

import jakarta.validation.constraints.Size;

public record UpdateLibraryRequest(
        @Size(max = 50)
        String code,

        @Size(max = 255)
        String name
) {
}
