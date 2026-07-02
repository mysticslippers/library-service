package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.user.request.CreateUserRequest;
import me.ifmo.backend.dto.user.response.UserAdminResponse;
import me.ifmo.backend.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
    }
}
