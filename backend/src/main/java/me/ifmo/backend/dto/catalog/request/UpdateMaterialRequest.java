package me.ifmo.backend.dto.catalog.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.ifmo.backend.entities.enums.MaterialType;

import java.util.List;
import java.util.Set;

public record UpdateMaterialRequest(
        @Pattern(regexp = "^[0-9]{10}([0-9]{3})?$")
        String isbn,

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
