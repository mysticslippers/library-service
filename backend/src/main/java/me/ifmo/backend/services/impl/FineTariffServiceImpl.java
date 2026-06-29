package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.fine.request.ChangeFineTariffStatusRequest;
import me.ifmo.backend.dto.fine.request.CreateFineTariffRequest;
import me.ifmo.backend.dto.fine.request.UpdateFineTariffRequest;
import me.ifmo.backend.dto.fine.response.FineTariffResponse;
import me.ifmo.backend.entities.FineTariff;
import me.ifmo.backend.entities.enums.FineTariffStatus;
import me.ifmo.backend.entities.enums.ViolationType;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
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

    @Override
    @Transactional(readOnly = true)
    public FineTariffResponse getFineTariffById(Long id) {
        FineTariff tariff = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine tariff with id '%s' not found".formatted(id)));

        return mapper.toResponse(tariff);
    }

    @Override
    @Transactional(readOnly = true)
    public FineTariffResponse getActualByViolationType(ViolationType violationType) {
        FineTariff tariff = repository.findActualByViolationTypeAndStatus(violationType, FineTariffStatus.ACTIVE,
                        LocalDateTime.now()).orElseThrow(
                        () -> new ResourceNotFoundException("Actual fine tariff for violation type '%s' not found".formatted(violationType)));

        return mapper.toResponse(tariff);
    }

    @Override
    @Transactional
    public FineTariffResponse update(Long id, UpdateFineTariffRequest request) {
        FineTariff tariff = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine tariff with id '%s' not found".formatted(id)));

        if (tariff.getStatus() == FineTariffStatus.ARCHIVED)
            throw new BusinessRuleException("Archived fine tariff cannot be updated");

        BigDecimal amountPerDay = request.amountPerDay() != null ? request.amountPerDay() : tariff.getAmountPerDay();
        BigDecimal fixedAmount = request.fixedAmount() != null ? request.fixedAmount() : tariff.getFixedAmount();
        BigDecimal maxAmount = request.maxAmount() != null ? request.maxAmount() : tariff.getMaxAmount();

        validate(amountPerDay, fixedAmount, maxAmount);

        if (request.validTo() != null && !request.validTo().isAfter(tariff.getValidFrom()))
            throw new BusinessRuleException("Fine tariff validTo must be after validFrom");

        mapper.updateEntity(request, tariff);

        FineTariff saved = repository.save(tariff);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public FineTariffResponse changeStatus(Long id, ChangeFineTariffStatusRequest request) {
        FineTariff tariff = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine tariff with id '%s' not found".formatted(id)));

        FineTariffStatus status = request.status();

        if (tariff.getStatus() == status)
            return mapper.toResponse(tariff);

        if (tariff.getStatus() == FineTariffStatus.ARCHIVED)
            throw new BusinessRuleException("Archived fine tariff status cannot be changed");

        LocalDateTime now = LocalDateTime.now();

        if (status == FineTariffStatus.ACTIVE) {
            repository.findByViolationTypeAndStatus(tariff.getViolationType(), FineTariffStatus.ACTIVE)
                    .filter(activeTariff -> !activeTariff.getId().equals(tariff.getId())).ifPresent(activeTariff -> {
                        activeTariff.setStatus(FineTariffStatus.INACTIVE);
                        activeTariff.setValidTo(now);
                    });

            tariff.setValidTo(null);
        }

        if (status == FineTariffStatus.INACTIVE || status == FineTariffStatus.ARCHIVED)
            tariff.setValidTo(now);

        tariff.setStatus(status);

        FineTariff saved = repository.save(tariff);
        return mapper.toResponse(saved);
    }

}
