package me.ifmo.backend.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class BranchAddressJsonMapper {

    private final ObjectMapper objectMapper;

    public BranchAddressJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
