package me.ifmo.backend.fine.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.fine.web.request.ChangeFineTariffStatusRequest;
import me.ifmo.backend.fine.web.request.CreateFineTariffRequest;
import me.ifmo.backend.fine.web.request.UpdateFineTariffRequest;
import me.ifmo.backend.fine.web.response.FineTariffResponse;
import me.ifmo.backend.fine.domain.enums.FineTariffStatus;
import me.ifmo.backend.fine.domain.enums.ViolationType;
import me.ifmo.backend.fine.application.FineTariffService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fine-tariffs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FineTariffController {

    private final FineTariffService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FineTariffResponse create(@Valid @RequestBody CreateFineTariffRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public FineTariffResponse getFineTariffById(@PathVariable Long id) {
        return service.getFineTariffById(id);
    }

    @GetMapping("/actual/{violationType}")
    public FineTariffResponse getActualByViolationType(@PathVariable ViolationType violationType) {
        return service.getActualByViolationType(violationType);
    }

    @PatchMapping("/{id}")
    public FineTariffResponse update(@PathVariable Long id, @Valid @RequestBody UpdateFineTariffRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public FineTariffResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeFineTariffStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping
    public PageResponse<FineTariffResponse> search(@RequestParam(required = false) ViolationType violationType, @RequestParam(required = false) FineTariffStatus status, Pageable pageable) {
        return service.search(violationType, status, pageable);
    }
}