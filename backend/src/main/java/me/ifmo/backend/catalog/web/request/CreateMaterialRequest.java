package me.ifmo.backend.catalog.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import me.ifmo.backend.catalog.domain.enums.MaterialType;

import java.util.List;
import java.util.Set;

public record CreateMaterialRequest(
        @Pattern(regexp = "^[0-9]{10}([0-9]{3})?$")
        String isbn,

        @NotBlank
        @Size(max = 255)
        String title,

        String description,

        @Size(max = 255)
        String publisher,

        @Min(1000)
        @Max(3000)
        Integer publicationYear,

        MaterialType materialType,

        @Size(max = 50)
        String language,

        @Valid
        List<MaterialAuthorRequest> authors,

        Set<Long> genreIds
) {
}
