package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.mappers.UserMapper;
import me.ifmo.backend.repositories.BranchRepository;
import me.ifmo.backend.repositories.RoleRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.repositories.UserRoleRepository;
import me.ifmo.backend.services.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;

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
}
