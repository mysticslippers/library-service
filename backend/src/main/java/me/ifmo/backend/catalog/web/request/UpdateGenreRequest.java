package me.ifmo.backend.catalog.web.request;

import jakarta.validation.constraints.Size;

public record UpdateGenreRequest(
        @Size(max = 50)
        String code,

        @Size(max = 100)
        String name
) {
}
