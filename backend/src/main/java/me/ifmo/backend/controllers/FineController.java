package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.fine.request.CancelFineRequest;
import me.ifmo.backend.dto.fine.request.CreateFineRequest;
import me.ifmo.backend.dto.fine.request.FineSearchRequest;
import me.ifmo.backend.dto.fine.response.FineResponse;
import me.ifmo.backend.services.FineService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fines")
@RequiredArgsConstructor
public class FineController {

    private final FineService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FineResponse create(@Valid @RequestBody CreateFineRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public FineResponse getFineById(@PathVariable Long id) {
        return service.getFineById(id);
    }

    @PostMapping("/{id}/cancel")
    public FineResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelFineRequest request) {
        return service.cancel(id, request);
    }

    @PostMapping("/{id}/mark-paid")
    public FineResponse markPaid(@PathVariable Long id) {
        return service.markPaid(id);
    }

    @GetMapping
    public PageResponse<FineResponse> search(@Valid @ModelAttribute FineSearchRequest request, Pageable pageable) {
        return service.search(request, pageable);
    }
}