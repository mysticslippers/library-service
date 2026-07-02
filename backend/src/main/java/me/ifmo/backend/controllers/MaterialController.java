package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateMaterialRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialRequest;
import me.ifmo.backend.dto.catalog.response.MaterialResponse;
import me.ifmo.backend.services.MaterialService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialResponse create(@Valid @RequestBody CreateMaterialRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public MaterialResponse getMaterialById(@PathVariable Long id) {
        return service.getMaterialById(id);
    }

    @GetMapping("/isbn/{isbn}")
    public MaterialResponse getMaterialByIsbn(@PathVariable String isbn) {
        return service.getMaterialByIsbn(isbn);
    }

    @PatchMapping("/{id}")
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody UpdateMaterialRequest request) {
        return service.update(id, request);
    }
}
