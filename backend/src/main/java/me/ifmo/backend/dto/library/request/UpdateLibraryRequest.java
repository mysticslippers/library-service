package me.ifmo.backend.dto.library.request;

import jakarta.validation.constraints.Size;

public record UpdateLibraryRequest(
        @Size(max = 50)
        String code,

        @Size(max = 255)
        String name
) {
}
