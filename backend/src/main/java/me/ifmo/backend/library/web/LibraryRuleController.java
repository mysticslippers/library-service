package me.ifmo.backend.library.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.web.request.ChangeLibraryRuleStatusRequest;
import me.ifmo.backend.library.web.request.CreateLibraryRuleRequest;
import me.ifmo.backend.library.web.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.library.web.response.LibraryRuleResponse;
import me.ifmo.backend.library.domain.enums.LibraryRuleStatus;
import me.ifmo.backend.library.application.LibraryRuleService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/library-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
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
