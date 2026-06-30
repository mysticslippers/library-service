package me.ifmo.backend.services.impl;

import me.ifmo.backend.mappers.AuditLogMapper;
import me.ifmo.backend.repositories.AuditLogRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.services.AuditLogService;

public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;
    private final AuditLogMapper mapper;
}
