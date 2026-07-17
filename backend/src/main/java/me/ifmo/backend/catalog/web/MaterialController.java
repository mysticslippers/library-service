package me.ifmo.backend.catalog.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.web.request.ChangeMaterialStatusRequest;
import me.ifmo.backend.catalog.web.request.CreateMaterialRequest;
import me.ifmo.backend.catalog.web.request.MaterialSearchRequest;
import me.ifmo.backend.catalog.web.request.UpdateMaterialRequest;
import me.ifmo.backend.catalog.web.response.MaterialResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.catalog.application.MaterialService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public MaterialResponse create(@Valid @RequestBody CreateMaterialRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public MaterialResponse getMaterialById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getMaterialById(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping("/isbn/{isbn}")
    public MaterialResponse getMaterialByIsbn(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String isbn) {
        return service.getMaterialByIsbn(Long.valueOf(userDetails.getUsername()), isbn);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody UpdateMaterialRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public MaterialResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeMaterialStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    public PageResponse<MaterialResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute MaterialSearchRequest request, Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}
