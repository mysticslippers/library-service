package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.library.request.CreateLibraryRequest;
import me.ifmo.backend.dto.library.request.UpdateLibraryRequest;
import me.ifmo.backend.dto.library.response.LibraryResponse;
import me.ifmo.backend.services.LibraryService;
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
}
