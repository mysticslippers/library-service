package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.AuthorSearchRequest;
import me.ifmo.backend.dto.catalog.request.CreateAuthorRequest;
import me.ifmo.backend.dto.catalog.request.UpdateAuthorRequest;
import me.ifmo.backend.dto.catalog.response.AuthorResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.services.AuthorService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public AuthorResponse create(@Valid @RequestBody CreateAuthorRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public AuthorResponse getAuthorById(@PathVariable Long id) {
        return service.getAuthorById(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public AuthorResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAuthorRequest request) {
        return service.update(id, request);
    }

    @GetMapping
    public PageResponse<AuthorResponse> search(@Valid @ModelAttribute AuthorSearchRequest request, Pageable pageable) {
        return service.search(request, pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
