package me.ifmo.backend.authentication.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.authentication.web.request.ActivateAccountRequest;
import me.ifmo.backend.authentication.web.request.LoginRequest;
import me.ifmo.backend.authentication.web.request.PasswordRecoveryRequest;
import me.ifmo.backend.authentication.web.request.PasswordResetRequest;
import me.ifmo.backend.authentication.web.request.RegisterRequest;
import me.ifmo.backend.authentication.web.request.ResendActivationRequest;
import me.ifmo.backend.authentication.web.response.AuthMessageResponse;
import me.ifmo.backend.authentication.web.response.AuthResponse;
import me.ifmo.backend.user.web.response.UserProfileResponse;
import me.ifmo.backend.authentication.application.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Account registration, activation, login, and password recovery")
public class AuthController {

    private final AuthService service;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new account")
    @SecurityRequirements
    public AuthMessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate a user and issue a JWT")
    @SecurityRequirements
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/activate")
    @Operation(summary = "Activate an account")
    @SecurityRequirements
    public AuthMessageResponse activate(@Valid @RequestBody ActivateAccountRequest request) {
        return service.activate(request);
    }

    @PostMapping("/resend-activation")
    @Operation(summary = "Resend an account activation token")
    @SecurityRequirements
    public AuthMessageResponse resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        return service.resendActivation(request);
    }

    @PostMapping("/password-recovery")
    @Operation(summary = "Request password recovery")
    @SecurityRequirements
    public AuthMessageResponse requestPasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request) {
        return service.requestPasswordRecovery(request);
    }

    @PostMapping("/password-reset")
    @Operation(summary = "Reset a password using a recovery token")
    @SecurityRequirements
    public AuthMessageResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return service.resetPassword(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user's profile")
    public UserProfileResponse me(@AuthenticationPrincipal UserDetails userDetails) {
        return service.me(Long.valueOf(userDetails.getUsername()));
    }
}
