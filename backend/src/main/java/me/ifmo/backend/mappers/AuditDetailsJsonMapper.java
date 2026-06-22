package me.ifmo.backend.mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditDetailsJsonMapper {

    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> DETAILS_TYPE = new TypeReference<>() {};

    public AuditDetailsJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
