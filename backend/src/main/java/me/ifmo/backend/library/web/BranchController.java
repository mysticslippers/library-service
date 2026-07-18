package me.ifmo.backend.library.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.web.request.ChangeBranchStatusRequest;
import me.ifmo.backend.library.web.request.CreateBranchRequest;
import me.ifmo.backend.library.web.request.UpdateBranchRequest;
import me.ifmo.backend.library.web.response.BranchResponse;
import me.ifmo.backend.library.domain.enums.BranchStatus;
import me.ifmo.backend.library.application.BranchService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Branches", description = "Administrative management of physical library branches")
public class BranchController {

    private final BranchService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a library branch")
    public BranchResponse create(@Valid @RequestBody CreateBranchRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a branch by ID")
    public BranchResponse getBranchById(@PathVariable Long id) {
        return service.getBranchById(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a library branch")
    public BranchResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBranchRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a branch's status")
    public BranchResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeBranchStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    @Operation(summary = "Search library branches")
    public PageResponse<BranchResponse> search(@RequestParam(required = false) Long libraryId, @RequestParam(required = false) BranchStatus status, @ParameterObject Pageable pageable) {
        return service.search(libraryId, status, pageable);
    }
}
