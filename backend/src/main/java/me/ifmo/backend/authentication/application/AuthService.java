package me.ifmo.backend.authentication.application;

import me.ifmo.backend.authentication.web.request.LoginRequest;
import me.ifmo.backend.authentication.web.request.ActivateAccountRequest;
import me.ifmo.backend.authentication.web.request.PasswordRecoveryRequest;
import me.ifmo.backend.authentication.web.request.PasswordResetRequest;
import me.ifmo.backend.authentication.web.request.RegisterRequest;
import me.ifmo.backend.authentication.web.request.ResendActivationRequest;
import me.ifmo.backend.authentication.web.response.AuthMessageResponse;
import me.ifmo.backend.authentication.web.response.AuthResponse;
import me.ifmo.backend.user.web.response.UserProfileResponse;

public interface AuthService {

    AuthMessageResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthMessageResponse activate(ActivateAccountRequest request);

    AuthMessageResponse resendActivation(ResendActivationRequest request);

    AuthMessageResponse requestPasswordRecovery(PasswordRecoveryRequest request);

    AuthMessageResponse resetPassword(PasswordResetRequest request);

    UserProfileResponse me(Long userId);
}
