package me.ifmo.backend.controllers;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.services.LibraryService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/libraries")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService service;
}
