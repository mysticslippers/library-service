package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.auth.request.ActivateAccountRequest;
import me.ifmo.backend.dto.auth.request.LoginRequest;
import me.ifmo.backend.dto.auth.request.PasswordRecoveryRequest;
import me.ifmo.backend.dto.auth.request.PasswordResetRequest;
import me.ifmo.backend.dto.auth.request.RegisterRequest;
import me.ifmo.backend.dto.auth.request.ResendActivationRequest;
import me.ifmo.backend.dto.auth.response.AuthMessageResponse;
import me.ifmo.backend.dto.auth.response.AuthResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;
import me.ifmo.backend.services.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthMessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/activate")
    public AuthMessageResponse activate(@Valid @RequestBody ActivateAccountRequest request) {
        return service.activate(request);
    }

    @PostMapping("/resend-activation")
    public AuthMessageResponse resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        return service.resendActivation(request);
    }

    @PostMapping("/password-recovery")
    public AuthMessageResponse requestPasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request) {
        return service.requestPasswordRecovery(request);
    }

    @PostMapping("/password-reset")
    public AuthMessageResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return service.resetPassword(request);
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal UserDetails userDetails) {
        return service.me(Long.valueOf(userDetails.getUsername()));
    }
}
