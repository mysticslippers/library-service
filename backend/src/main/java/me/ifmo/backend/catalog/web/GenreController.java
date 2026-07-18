package me.ifmo.backend.catalog.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.web.request.CreateGenreRequest;
import me.ifmo.backend.catalog.web.request.UpdateGenreRequest;
import me.ifmo.backend.catalog.web.response.GenreResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.catalog.application.GenreService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/genres")
@RequiredArgsConstructor
@Tag(name = "Genres", description = "Catalog genre management")
public class GenreController {

    private final GenreService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Create a genre")
    public GenreResponse create(@Valid @RequestBody CreateGenreRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a genre by ID")
    public GenreResponse getGenreById(@PathVariable Long id) {
        return service.getGenreById(id);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get a genre by code")
    public GenreResponse getGenreByCode(@PathVariable String code) {
        return service.getGenreByCode(code);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Update a genre")
    public GenreResponse update(@PathVariable Long id, @Valid @RequestBody UpdateGenreRequest request) {
        return service.update(id, request);
    }

    @GetMapping
    @Operation(summary = "Search genres")
    public PageResponse<GenreResponse> search(@RequestParam(required = false) String query, @ParameterObject Pageable pageable) {
        return service.search(query, pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Delete a genre")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
