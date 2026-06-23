package me.ifmo.backend.exceptions.domain;

import me.ifmo.backend.exceptions.ApiException;
import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {
    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", message);
    }
}
