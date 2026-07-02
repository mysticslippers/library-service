package me.ifmo.backend.controllers;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.services.MaterialCopyService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/material-copies")
@RequiredArgsConstructor
public class MaterialCopyController {

    private final MaterialCopyService service;
}
