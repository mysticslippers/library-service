package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.constraints.Size;

public record UpdateGenreRequest(
        @Size(max = 50)
        String code,

        @Size(max = 100)
        String name
) {
}
