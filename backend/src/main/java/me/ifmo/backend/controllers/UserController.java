package me.ifmo.backend.controllers;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.services.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;
}
