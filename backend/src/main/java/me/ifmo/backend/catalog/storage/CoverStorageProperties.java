package me.ifmo.backend.catalog.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "storage.covers")
public record CoverStorageProperties(
        @NotBlank
        String endpoint,

        @NotBlank
        String region,

        @NotBlank
        String accessKey,

        @NotBlank
        String secretKey,

        @NotBlank
        String bucket,

        boolean pathStyleAccess,
        boolean createBucket,

        @NotNull
        Duration apiCallTimeout,

        @NotNull
        DataSize maxFileSize
) {
}
