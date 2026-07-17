package me.ifmo.backend.library.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.ifmo.backend.library.web.request.BranchAddressRequest;
import me.ifmo.backend.library.web.response.BranchAddressResponse;
import org.springframework.stereotype.Component;

@Component
public class BranchAddressJsonMapper {

    private final ObjectMapper objectMapper;

    public BranchAddressJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode toJsonNode(BranchAddressRequest address) {
        if (address == null)
            return null;

        return objectMapper.valueToTree(address);
    }

    public BranchAddressResponse toResponse(JsonNode addressJson) {
        if (addressJson == null || addressJson.isNull())
            return null;

        return objectMapper.convertValue(addressJson, BranchAddressResponse.class);
    }
}
