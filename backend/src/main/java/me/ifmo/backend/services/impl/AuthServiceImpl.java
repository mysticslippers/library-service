package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.auth.response.AuthResponse;
import me.ifmo.backend.entities.Role;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserRole;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.entities.id.UserRoleId;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.UserMapper;
import me.ifmo.backend.repositories.RoleRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.repositories.UserRoleRepository;
import me.ifmo.backend.services.AuthService;
import me.ifmo.backend.services.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final short MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private String normalize(String value, String fieldName) {
        if(fieldName.equals("Middle name")){
            if (value == null || value.strip().isBlank())
                return null;

        } else {
            if(fieldName.equals("Email"))
                value = value.toLowerCase(Locale.ROOT);

            if (value == null || value.strip().isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));
        }
        return value.strip();
    }

    private AuthResponse toAuthResponse(User user) {
        List<RoleCode> roles = userRoleRepository.findRoleCodesByUser_Id(user.getId());
        String token = jwtService.generateAccessToken(user, roles);

        return new AuthResponse(token, "Bearer", jwtService.getAccessTokenExpiresIn(),
                userMapper.toProfileResponse(user, new LinkedHashSet<>(roles)));
    }

    private void assignDefaultReaderRole(User user) {
        Role role = roleRepository.findByCode(RoleCode.READER).orElseThrow(
                () -> new ResourceNotFoundException("Role with code '%s' not found".formatted(RoleCode.READER)));

        UserRole userRole = UserRole.builder().id(new UserRoleId(user.getId(), role.getId())).user(user).role(role).build();

        userRoleRepository.save(userRole);
    }

    private void rejectInvalidCredentials(User user) {
        short attempts = (short) (user.getFailedLoginAttempts() + 1);
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS)
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));

        userRepository.save(user);

        throw new BusinessRuleException("Invalid email or password");
    }

    private void validate(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("User is temporarily locked");

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("User account is not active");
    }
}
