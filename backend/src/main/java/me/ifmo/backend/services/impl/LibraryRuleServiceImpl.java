package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.library.request.CreateLibraryRuleRequest;
import me.ifmo.backend.dto.library.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.dto.library.response.LibraryRuleResponse;
import me.ifmo.backend.entities.Branch;
import me.ifmo.backend.entities.LibraryRule;
import me.ifmo.backend.entities.enums.BranchStatus;
import me.ifmo.backend.entities.enums.LibraryRuleStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.LibraryRuleMapper;
import me.ifmo.backend.repositories.BranchRepository;
import me.ifmo.backend.repositories.LibraryRuleRepository;
import me.ifmo.backend.services.LibraryRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LibraryRuleServiceImpl implements LibraryRuleService {

    private final LibraryRuleRepository repository;
    private final BranchRepository branchRepository;
    private final LibraryRuleMapper mapper;

    @Override
    @Transactional
    public LibraryRuleResponse create(CreateLibraryRuleRequest request) {
        Branch branch = branchRepository.findById(request.branchId()).orElseThrow(
                () -> new ResourceNotFoundException("Branch with id '%s' not found".formatted(request.branchId())));

        if (branch.getStatus() == BranchStatus.ARCHIVED)
            throw new BusinessRuleException("Library rule cannot be created for archived branch");

        repository.findByBranch_IdAndStatus(branch.getId(), LibraryRuleStatus.ACTIVE)
                .ifPresent(activeRule -> {
                    activeRule.setStatus(LibraryRuleStatus.INACTIVE);
                    activeRule.setValidTo(LocalDateTime.now());
                });

        LibraryRule rule = mapper.toEntity(request, branch);
        rule.setStatus(LibraryRuleStatus.ACTIVE);
        rule.setValidTo(null);

        LibraryRule saved = repository.save(rule);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryRuleResponse getLibraryRuleById(Long id) {
        LibraryRule rule = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Library rule with id '%s' not found".formatted(id)));

        return mapper.toResponse(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryRuleResponse getActualByBranchId(Long branchId) {
        LibraryRule rule = repository.findActualByBranchIdAndStatus(branchId, LibraryRuleStatus.ACTIVE,
                LocalDateTime.now()).orElseThrow(() ->
                new ResourceNotFoundException("Actual library rule for branch with id '%s' not found".formatted(branchId)));

        return mapper.toResponse(rule);
    }

    @Override
    @Transactional
    public LibraryRuleResponse update(Long id, UpdateLibraryRuleRequest request) {
        LibraryRule rule = repository.findById(id).orElseThrow(() ->
                        new ResourceNotFoundException("Library rule with id '%s' not found".formatted(id)));

        if (rule.getStatus() == LibraryRuleStatus.ARCHIVED)
            throw new BusinessRuleException("Archived library rule cannot be updated");

        if (request.validTo() != null && !request.validTo().isAfter(rule.getValidFrom()))
            throw new BusinessRuleException("Library rule validTo must be after validFrom");

        mapper.updateEntity(request, rule);

        LibraryRule saved = repository.save(rule);
        return mapper.toResponse(saved);
    }
}
