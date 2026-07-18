package me.ifmo.backend.library.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.web.request.ChangeLibraryRuleStatusRequest;
import me.ifmo.backend.library.web.request.CreateLibraryRuleRequest;
import me.ifmo.backend.library.web.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.library.web.response.LibraryRuleResponse;
import me.ifmo.backend.library.domain.enums.LibraryRuleStatus;
import me.ifmo.backend.library.application.LibraryRuleService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/library-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Library Rules", description = "Versioned circulation rules configured per branch")
public class LibraryRuleController {

    private final LibraryRuleService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a library rule")
    public LibraryRuleResponse create(@Valid @RequestBody CreateLibraryRuleRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a library rule by ID")
    public LibraryRuleResponse getLibraryRuleById(@PathVariable Long id) {
        return service.getLibraryRuleById(id);
    }

    @GetMapping("/branches/{branchId}/actual")
    @Operation(summary = "Get the active rule for a branch")
    public LibraryRuleResponse getActualByBranchId(@PathVariable Long branchId) {
        return service.getActualByBranchId(branchId);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a library rule")
    public LibraryRuleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLibraryRuleRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a library rule's status")
    public LibraryRuleResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeLibraryRuleStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    @Operation(summary = "Search library rules")
    public PageResponse<LibraryRuleResponse> search(@RequestParam(required = false) Long branchId, @RequestParam(required = false) LibraryRuleStatus status, @ParameterObject Pageable pageable) {
        return service.search(branchId, status, pageable);
    }
}
