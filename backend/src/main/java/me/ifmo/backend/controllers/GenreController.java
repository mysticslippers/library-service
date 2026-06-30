package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateGenreRequest;
import me.ifmo.backend.dto.catalog.request.UpdateGenreRequest;
import me.ifmo.backend.dto.catalog.response.GenreResponse;
import me.ifmo.backend.services.GenreService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GenreResponse create(@Valid @RequestBody CreateGenreRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public GenreResponse getGenreById(@PathVariable Long id) {
        return service.getGenreById(id);
    }

    @GetMapping("/code/{code}")
    public GenreResponse getGenreByCode(@PathVariable String code) {
        return service.getGenreByCode(code);
    }

    @PatchMapping("/{id}")
    public GenreResponse update(@PathVariable Long id, @Valid @RequestBody UpdateGenreRequest request) {
        return service.update(id, request);
    }
}
