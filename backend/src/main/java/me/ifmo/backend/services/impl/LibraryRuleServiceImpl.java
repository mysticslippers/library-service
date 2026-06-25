package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.mappers.LibraryRuleMapper;
import me.ifmo.backend.repositories.BranchRepository;
import me.ifmo.backend.repositories.LibraryRuleRepository;
import me.ifmo.backend.services.LibraryRuleService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LibraryRuleServiceImpl implements LibraryRuleService {

    private final LibraryRuleRepository repository;
    private final BranchRepository branchRepository;
    private final LibraryRuleMapper mapper;
}
