package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.library.request.CreateLibraryRuleRequest;
import me.ifmo.backend.dto.library.response.LibraryRuleResponse;
import me.ifmo.backend.services.LibraryRuleService;
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
}
