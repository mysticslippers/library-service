package me.ifmo.backend.shared.observability.configuration;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
@Validated
@ConfigurationProperties("security.monitoring")
public class MonitoringProperties {

    private boolean enabled = false;
    private String password = "";

    @AssertTrue(message = "Enabled monitoring requires a password of 32 to 72 UTF-8 bytes")
    public boolean isPasswordValid() {
        if (!enabled)
            return true;

        if (password == null || password.isBlank())
            return false;

        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        return bytes >= 32 && bytes <= 72;
    }
}
