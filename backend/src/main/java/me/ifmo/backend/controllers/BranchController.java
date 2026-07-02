package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.library.request.ChangeBranchStatusRequest;
import me.ifmo.backend.dto.library.request.CreateBranchRequest;
import me.ifmo.backend.dto.library.request.UpdateBranchRequest;
import me.ifmo.backend.dto.library.response.BranchResponse;
import me.ifmo.backend.entities.enums.BranchStatus;
import me.ifmo.backend.services.BranchService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponse create(@Valid @RequestBody CreateBranchRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public BranchResponse getBranchById(@PathVariable Long id) {
        return service.getBranchById(id);
    }

    @PatchMapping("/{id}")
    public BranchResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBranchRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public BranchResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeBranchStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    public PageResponse<BranchResponse> search(@RequestParam(required = false) Long libraryId, @RequestParam(required = false) BranchStatus status, Pageable pageable) {
        return service.search(libraryId, status, pageable);
    }
}
