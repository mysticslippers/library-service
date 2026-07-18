package me.ifmo.backend.catalog.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.web.request.ChangeMaterialCopyStatusRequest;
import me.ifmo.backend.catalog.web.request.CreateMaterialCopyRequest;
import me.ifmo.backend.catalog.web.request.UpdateMaterialCopyRequest;
import me.ifmo.backend.catalog.web.response.MaterialCopyResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;
import me.ifmo.backend.catalog.application.MaterialCopyService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/material-copies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
@Tag(name = "Material Copies", description = "Physical inventory copies of catalog materials")
public class MaterialCopyController {

    private final MaterialCopyService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a material copy")
    public MaterialCopyResponse create(@Valid @RequestBody CreateMaterialCopyRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a material copy by ID")
    public MaterialCopyResponse getMaterialCopyById(@PathVariable Long id) {
        return service.getMaterialCopyById(id);
    }

    @GetMapping("/inventory/{inventoryNumber}")
    @Operation(summary = "Get a material copy by inventory number")
    public MaterialCopyResponse getByInventoryNumber(@PathVariable String inventoryNumber) {
        return service.getByInventoryNumber(inventoryNumber);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a material copy")
    public MaterialCopyResponse update(@PathVariable Long id, @Valid @RequestBody UpdateMaterialCopyRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a material copy's status")
    public MaterialCopyResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeMaterialCopyStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    @Operation(summary = "Search material copies")
    public PageResponse<MaterialCopyResponse> search(@RequestParam(required = false) Long materialId, @RequestParam(required = false) Long branchId, @RequestParam(required = false) CopyStatus status, @ParameterObject Pageable pageable) {
        return service.search(materialId, branchId, status, pageable);
    }
}
