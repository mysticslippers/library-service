package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.catalog.request.ChangeMaterialCopyStatusRequest;
import me.ifmo.backend.dto.catalog.request.CreateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.request.UpdateMaterialCopyRequest;
import me.ifmo.backend.dto.catalog.response.MaterialCopyResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.entities.enums.CopyStatus;
import me.ifmo.backend.services.MaterialCopyService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/material-copies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
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

    @PatchMapping("/{id}")
    public MaterialCopyResponse update(@PathVariable Long id, @Valid @RequestBody UpdateMaterialCopyRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public MaterialCopyResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeMaterialCopyStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    public PageResponse<MaterialCopyResponse> search(@RequestParam(required = false) Long materialId, @RequestParam(required = false) Long branchId, @RequestParam(required = false) CopyStatus status, Pageable pageable) {
        return service.search(materialId, branchId, status, pageable);
    }
}
