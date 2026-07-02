package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateAuthorRequest;
import me.ifmo.backend.dto.catalog.response.AuthorResponse;
import me.ifmo.backend.services.AuthorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorResponse create(@Valid @RequestBody CreateAuthorRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public AuthorResponse getAuthorById(@PathVariable Long id) {
        return service.getAuthorById(id);
    }
}
