package me.ifmo.backend.library.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.web.request.ChangeLibraryStatusRequest;
import me.ifmo.backend.library.web.request.CreateLibraryRequest;
import me.ifmo.backend.library.web.request.UpdateLibraryRequest;
import me.ifmo.backend.library.web.response.LibraryResponse;
import me.ifmo.backend.library.domain.enums.LibraryStatus;
import me.ifmo.backend.library.application.LibraryService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/libraries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Libraries", description = "Administrative management of library organizations")
public class LibraryController {

    private final LibraryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a library")
    public LibraryResponse create(@Valid @RequestBody CreateLibraryRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a library by ID")
    public LibraryResponse getLibraryById(@PathVariable Long id) {
        return service.getLibraryById(id);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get a library by code")
    public LibraryResponse getByCode(@PathVariable String code) {
        return service.getByCode(code);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a library")
    public LibraryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLibraryRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a library's status")
    public LibraryResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeLibraryStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    @Operation(summary = "Search libraries")
    public PageResponse<LibraryResponse> search(@RequestParam(required = false) String query, @RequestParam(required = false) LibraryStatus status, @ParameterObject Pageable pageable) {
        return service.search(query, status, pageable);
    }
}
