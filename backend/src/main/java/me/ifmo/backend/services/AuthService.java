package me.ifmo.backend.services;

import me.ifmo.backend.dto.auth.request.LoginRequest;
import me.ifmo.backend.dto.auth.request.ActivateAccountRequest;
import me.ifmo.backend.dto.auth.request.PasswordRecoveryRequest;
import me.ifmo.backend.dto.auth.request.PasswordResetRequest;
import me.ifmo.backend.dto.auth.request.RegisterRequest;
import me.ifmo.backend.dto.auth.request.ResendActivationRequest;
import me.ifmo.backend.dto.auth.response.AuthMessageResponse;
import me.ifmo.backend.dto.auth.response.AuthResponse;
import me.ifmo.backend.dto.user.response.UserProfileResponse;

public interface AuthService {

    AuthMessageResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthMessageResponse activate(ActivateAccountRequest request);

    AuthMessageResponse resendActivation(ResendActivationRequest request);

    AuthMessageResponse requestPasswordRecovery(PasswordRecoveryRequest request);

    AuthMessageResponse resetPassword(PasswordResetRequest request);

    UserProfileResponse me(Long userId);
}
