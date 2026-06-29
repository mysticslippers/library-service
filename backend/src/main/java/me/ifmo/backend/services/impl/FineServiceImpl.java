package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.mappers.FineMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.FineService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FineServiceImpl implements FineService {

    private final FineRepository repository;
    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final MaterialCopyRepository materialCopyRepository;
    private final FineTariffRepository fineTariffRepository;
    private final FineMapper mapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }
}
