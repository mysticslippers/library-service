package me.ifmo.backend.audit.mapper;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditDetailsJsonMapper {

    public Map<String, Object> toMap(Map<String, Object> details) {
        return details != null ? details : Map.of();
    }
}
