package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.CreateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.response.MaterialCopyResponse;
import me.ifmo.backend.services.MaterialCopyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/material-copies")
@RequiredArgsConstructor
public class MaterialCopyController {

    private final MaterialCopyService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialCopyResponse create(@Valid @RequestBody CreateMaterialCopyRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public MaterialCopyResponse getMaterialCopyById(@PathVariable Long id) {
        return service.getMaterialCopyById(id);
    }

    @GetMapping("/inventory/{inventoryNumber}")
    public MaterialCopyResponse getByInventoryNumber(@PathVariable String inventoryNumber) {
        return service.getByInventoryNumber(inventoryNumber);
    }
}
