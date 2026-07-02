package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.library.request.ChangeLibraryRuleStatusRequest;
import me.ifmo.backend.dto.library.request.CreateLibraryRuleRequest;
import me.ifmo.backend.dto.library.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.dto.library.response.LibraryRuleResponse;
import me.ifmo.backend.entities.enums.LibraryRuleStatus;
import me.ifmo.backend.services.LibraryRuleService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/library-rules")
@RequiredArgsConstructor
public class LibraryRuleController {

    private final LibraryRuleService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryRuleResponse create(@Valid @RequestBody CreateLibraryRuleRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public LibraryRuleResponse getLibraryRuleById(@PathVariable Long id) {
        return service.getLibraryRuleById(id);
    }

    @GetMapping("/branches/{branchId}/actual")
    public LibraryRuleResponse getActualByBranchId(@PathVariable Long branchId) {
        return service.getActualByBranchId(branchId);
    }

    @PatchMapping("/{id}")
    public LibraryRuleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLibraryRuleRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public LibraryRuleResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeLibraryRuleStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    public PageResponse<LibraryRuleResponse> search(@RequestParam(required = false) Long branchId, @RequestParam(required = false) LibraryRuleStatus status, Pageable pageable) {
        return service.search(branchId, status, pageable);
    }
}
