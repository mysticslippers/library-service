package me.ifmo.backend.catalog.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.web.request.AuthorSearchRequest;
import me.ifmo.backend.catalog.web.request.CreateAuthorRequest;
import me.ifmo.backend.catalog.web.request.UpdateAuthorRequest;
import me.ifmo.backend.catalog.web.response.AuthorResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.catalog.application.AuthorService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/authors")
@RequiredArgsConstructor
@Tag(name = "Authors", description = "Catalog author management")
public class AuthorController {

    private final AuthorService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Create an author")
    public AuthorResponse create(@Valid @RequestBody CreateAuthorRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an author by ID")
    public AuthorResponse getAuthorById(@PathVariable Long id) {
        return service.getAuthorById(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Update an author")
    public AuthorResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAuthorRequest request) {
        return service.update(id, request);
    }

    @GetMapping
    @Operation(summary = "Search authors")
    public PageResponse<AuthorResponse> search(@Valid @ModelAttribute AuthorSearchRequest request, @ParameterObject Pageable pageable) {
        return service.search(request, pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Delete an author")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
