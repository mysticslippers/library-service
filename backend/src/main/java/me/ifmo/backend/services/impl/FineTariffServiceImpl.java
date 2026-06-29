package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.fine.request.CreateFineTariffRequest;
import me.ifmo.backend.dto.fine.response.FineTariffResponse;
import me.ifmo.backend.entities.FineTariff;
import me.ifmo.backend.entities.enums.FineTariffStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.mappers.FineTariffMapper;
import me.ifmo.backend.repositories.FineTariffRepository;
import me.ifmo.backend.services.FineTariffService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FineTariffServiceImpl implements FineTariffService {

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

    @Override
    @Transactional
    public FineTariffResponse create(CreateFineTariffRequest request) {
        validate(request.amountPerDay(), request.fixedAmount(), request.maxAmount());

        LocalDateTime now = LocalDateTime.now();

        repository.findByViolationTypeAndStatus(request.violationType(), FineTariffStatus.ACTIVE).ifPresent(activeTariff -> {
                    activeTariff.setStatus(FineTariffStatus.INACTIVE);
                    activeTariff.setValidTo(now);
                });

        FineTariff tariff = mapper.toEntity(request);
        tariff.setStatus(FineTariffStatus.ACTIVE);
        tariff.setValidTo(request.validTo());

        FineTariff saved = repository.save(tariff);
        return mapper.toResponse(saved);
    }
}
