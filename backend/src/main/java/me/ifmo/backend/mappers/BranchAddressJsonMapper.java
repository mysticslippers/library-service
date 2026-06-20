package me.ifmo.backend.mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.ifmo.backend.dto.library.request.BranchAddressRequest;
import org.springframework.stereotype.Component;

@Component
public class BranchAddressJsonMapper {

    private final ObjectMapper objectMapper;

    public BranchAddressJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(BranchAddressRequest address) {
        if (address == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(address);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize branch address", exception);
        }
    }
}
