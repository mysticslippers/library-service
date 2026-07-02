package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.library.request.ChangeLibraryStatusRequest;
import me.ifmo.backend.dto.library.request.CreateLibraryRequest;
import me.ifmo.backend.dto.library.request.UpdateLibraryRequest;
import me.ifmo.backend.dto.library.response.LibraryResponse;
import me.ifmo.backend.entities.enums.LibraryStatus;
import me.ifmo.backend.services.LibraryService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/libraries")
@RequiredArgsConstructor
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
