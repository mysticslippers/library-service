package me.ifmo.backend.library.internal.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.internal.web.request.ChangeLibraryStatusRequest;
import me.ifmo.backend.library.internal.web.request.CreateLibraryRequest;
import me.ifmo.backend.library.internal.web.request.UpdateLibraryRequest;
import me.ifmo.backend.library.internal.web.response.LibraryResponse;
import me.ifmo.backend.library.internal.domain.enums.LibraryStatus;
import me.ifmo.backend.library.internal.application.LibraryService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/libraries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LibraryController {

    private final LibraryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryResponse create(@Valid @RequestBody CreateLibraryRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public LibraryResponse getLibraryById(@PathVariable Long id) {
        return service.getLibraryById(id);
    }

    @GetMapping("/code/{code}")
    public LibraryResponse getByCode(@PathVariable String code) {
        return service.getByCode(code);
    }

    @PatchMapping("/{id}")
    public LibraryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLibraryRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public LibraryResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeLibraryStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    public PageResponse<LibraryResponse> search(@RequestParam(required = false) String query, @RequestParam(required = false) LibraryStatus status, Pageable pageable) {
        return service.search(query, status, pageable);
    }
}