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

    public String toJson(Map<String, Object> details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize audit details", exception
            );
        }
    }

    public Map<String, Object> toMap(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(detailsJson, DETAILS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize audit details", exception
            );
        }
    }
}
