package me.ifmo.backend.catalog.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(CoverStorageProperties.class)
public class S3StorageConfig {

    @Bean
    public S3Client coverS3Client(CoverStorageProperties properties) {
        var credentials = AwsBasicCredentials.create(properties.accessKey(), properties.secretKey());
        var overrides = ClientOverrideConfiguration.builder()
                .apiCallTimeout(properties.apiCallTimeout())
                .apiCallAttemptTimeout(properties.apiCallTimeout())
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(properties.pathStyleAccess())
                .overrideConfiguration(overrides)
                .build();
    }
}
