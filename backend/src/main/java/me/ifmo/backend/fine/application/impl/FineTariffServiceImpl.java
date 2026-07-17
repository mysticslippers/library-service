package me.ifmo.backend.fine.application.impl;

import me.ifmo.backend.fine.domain.Fine;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.fine.web.request.ChangeFineTariffStatusRequest;
import me.ifmo.backend.fine.web.request.CreateFineTariffRequest;
import me.ifmo.backend.fine.web.request.UpdateFineTariffRequest;
import me.ifmo.backend.fine.web.response.FineTariffResponse;
import me.ifmo.backend.fine.domain.FineTariff;
import me.ifmo.backend.fine.domain.enums.FineTariffStatus;
import me.ifmo.backend.fine.domain.enums.ViolationType;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.fine.mapper.FineTariffMapper;
import me.ifmo.backend.fine.persistence.FineTariffRepository;
import me.ifmo.backend.fine.application.FineTariffService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FineTariffServiceImpl implements FineTariffService {

    private final FineTariffRepository repository;
    private final FineTariffMapper fineTariffMapper;

    private void deactivateActiveTariff(ViolationType violationType, LocalDateTime now, Long excludedTariffId) {
        repository.findByViolationTypeAndStatus(violationType, FineTariffStatus.ACTIVE)
                .filter(activeTariff -> !activeTariff.getId().equals(excludedTariffId))
                .ifPresent(activeTariff -> {
                    activeTariff.setStatus(FineTariffStatus.INACTIVE);
                    activeTariff.setValidTo(now);
                    repository.saveAndFlush(activeTariff);
                });
    }

    private void validate(BigDecimal amountPerDay, BigDecimal fixedAmount, BigDecimal maxAmount) {
        boolean hasAmountPerDay = amountPerDay != null && amountPerDay.compareTo(BigDecimal.ZERO) > 0;
        boolean hasFixedAmount = fixedAmount != null && fixedAmount.compareTo(BigDecimal.ZERO) > 0;

        if (!hasAmountPerDay && !hasFixedAmount)
            throw new BusinessRuleException("Fine tariff must have amountPerDay or fixedAmount");

        if (maxAmount != null && maxAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessRuleException("Fine tariff maxAmount must be positive");

        if (maxAmount != null && fixedAmount != null && maxAmount.compareTo(fixedAmount) < 0)
            throw new BusinessRuleException("Fine tariff maxAmount must not be less than fixedAmount");

        if (maxAmount != null && amountPerDay != null && maxAmount.compareTo(amountPerDay) < 0)
            throw new BusinessRuleException("Fine tariff maxAmount must not be less than amountPerDay");
    }

    @Override
    @Transactional
    public FineTariffResponse create(CreateFineTariffRequest request) {
        validate(request.amountPerDay(), request.fixedAmount(), request.maxAmount());

        LocalDateTime now = LocalDateTime.now();

        if (request.validTo() != null && !request.validTo().isAfter(now))
            throw new BusinessRuleException("Fine tariff validTo must be in the future");

        deactivateActiveTariff(request.violationType(), now, null);

        FineTariff tariff = fineTariffMapper.toEntity(request);
        tariff.setStatus(FineTariffStatus.ACTIVE);
        tariff.setValidTo(request.validTo());

        FineTariff saved = repository.save(tariff);
        return fineTariffMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FineTariffResponse getFineTariffById(Long id) {
        FineTariff tariff = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine tariff with id '%s' not found".formatted(id)));

        return fineTariffMapper.toResponse(tariff);
    }

    @Override
    @Transactional(readOnly = true)
    public FineTariffResponse getActualByViolationType(ViolationType violationType) {
        FineTariff tariff = repository.findActualByViolationTypeAndStatus(violationType, FineTariffStatus.ACTIVE,
                        LocalDateTime.now()).orElseThrow(
                        () -> new ResourceNotFoundException("Actual fine tariff for violation type '%s' not found".formatted(violationType)));

        return fineTariffMapper.toResponse(tariff);
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

        fineTariffMapper.updateEntity(request, tariff);

        FineTariff saved = repository.save(tariff);
        return fineTariffMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public FineTariffResponse changeStatus(Long id, ChangeFineTariffStatusRequest request) {
        FineTariff tariff = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Fine tariff with id '%s' not found".formatted(id)));

        FineTariffStatus status = request.status();

        if (tariff.getStatus() == status)
            return fineTariffMapper.toResponse(tariff);

        if (tariff.getStatus() == FineTariffStatus.ARCHIVED)
            throw new BusinessRuleException("Archived fine tariff status cannot be changed");

        LocalDateTime now = LocalDateTime.now();

        if (status == FineTariffStatus.ACTIVE) {
            deactivateActiveTariff(tariff.getViolationType(), now, tariff.getId());

            tariff.setValidTo(null);
        }

        if (status == FineTariffStatus.INACTIVE || status == FineTariffStatus.ARCHIVED)
            tariff.setValidTo(now);

        tariff.setStatus(status);

        FineTariff saved = repository.save(tariff);
        return fineTariffMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FineTariffResponse> search(ViolationType violationType, FineTariffStatus status, Pageable pageable) {
        Page<FineTariff> tariffs;

        if (violationType == null && status == null)
            tariffs = repository.findAll(pageable);
        else if (violationType == null)
            tariffs = repository.findByStatus(status, pageable);
        else if (status == null)
            tariffs = repository.findByViolationType(violationType, pageable);
        else
            tariffs = repository.findByViolationTypeAndStatus(violationType, status, pageable);

        Page<FineTariffResponse> responses = tariffs.map(fineTariffMapper::toResponse);

        return PageResponse.from(responses);
    }
}
