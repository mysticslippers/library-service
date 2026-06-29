package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.mappers.FineTariffMapper;
import me.ifmo.backend.repositories.FineTariffRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class FineTariffServiceImpl {

    private final FineTariffRepository repository;
    private final FineTariffMapper mapper;

    private void validate(BigDecimal amountPerDay, BigDecimal fixedAmount, BigDecimal maxAmount) {
        boolean hasAmountPerDay = amountPerDay != null && amountPerDay.compareTo(BigDecimal.ZERO) > 0;
        boolean hasFixedAmount = fixedAmount != null && fixedAmount.compareTo(BigDecimal.ZERO) > 0;

        if (!hasAmountPerDay && !hasFixedAmount)
            throw new BusinessRuleException("Fine tariff must have amountPerDay or fixedAmount");

        if (maxAmount != null && maxAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessRuleException("Fine tariff maxAmount must be positive");

        if (maxAmount != null && fixedAmount != null && maxAmount.compareTo(fixedAmount) < 0)
            throw new BusinessRuleException("Fine tariff maxAmount must not be less than fixedAmount");
    }
}
