package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.library.request.ChangeLibraryRuleStatusRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LibraryRuleServiceImpl implements LibraryRuleService {

    private final LibraryRuleRepository repository;
    private final BranchRepository branchRepository;
    private final LibraryRuleMapper libraryRuleMapper;

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

        LibraryRule rule = libraryRuleMapper.toEntity(request, branch);
        rule.setStatus(LibraryRuleStatus.ACTIVE);
        rule.setValidTo(null);

        LibraryRule saved = repository.save(rule);
        return libraryRuleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryRuleResponse getLibraryRuleById(Long id) {
        LibraryRule rule = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Library rule with id '%s' not found".formatted(id)));

        return libraryRuleMapper.toResponse(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryRuleResponse getActualByBranchId(Long branchId) {
        LibraryRule rule = repository.findActualByBranchIdAndStatus(branchId, LibraryRuleStatus.ACTIVE,
                LocalDateTime.now()).orElseThrow(() ->
                new ResourceNotFoundException("Actual library rule for branch with id '%s' not found".formatted(branchId)));

        return libraryRuleMapper.toResponse(rule);
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

        libraryRuleMapper.updateEntity(request, rule);

        LibraryRule saved = repository.save(rule);
        return libraryRuleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LibraryRuleResponse changeStatus(Long id, ChangeLibraryRuleStatusRequest request) {
        LibraryRule rule = repository.findById(id).orElseThrow(() ->
                        new ResourceNotFoundException("Library rule with id '%s' not found".formatted(id)));

        LibraryRuleStatus status = request.status();

        if (rule.getStatus() == status)
            return libraryRuleMapper.toResponse(rule);

        if (rule.getStatus() == LibraryRuleStatus.ARCHIVED)
            throw new BusinessRuleException("Archived library rule status cannot be changed");

        LocalDateTime now = LocalDateTime.now();

        if (status == LibraryRuleStatus.ACTIVE) {
            if (rule.getBranch().getStatus() == BranchStatus.ARCHIVED)
                throw new BusinessRuleException("Library rule cannot be activated for archived branch");

            repository.findByBranch_IdAndStatus(rule.getBranch().getId(), LibraryRuleStatus.ACTIVE)
                    .filter(activeRule -> !activeRule.getId().equals(rule.getId()))
                    .ifPresent(activeRule -> {
                        activeRule.setStatus(LibraryRuleStatus.INACTIVE);
                        activeRule.setValidTo(now);
                    });

            rule.setValidTo(null);
        }

        if (status == LibraryRuleStatus.INACTIVE || status == LibraryRuleStatus.ARCHIVED)
            rule.setValidTo(now);

        rule.setStatus(status);

        LibraryRule saved = repository.save(rule);
        return libraryRuleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LibraryRuleResponse> search(Long branchId, LibraryRuleStatus status, Pageable pageable) {
        Page<LibraryRule> rules;

        if (branchId == null && status == null)
            rules = repository.findAll(pageable);
        else if (branchId == null)
            rules = repository.findByStatus(status, pageable);
        else if (status == null)
            rules = repository.findByBranch_Id(branchId, pageable);
        else
            rules = repository.findByBranch_IdAndStatus(branchId, status, pageable);

        Page<LibraryRuleResponse> responses = rules.map(libraryRuleMapper::toResponse);
        return PageResponse.from(responses);
    }
}
