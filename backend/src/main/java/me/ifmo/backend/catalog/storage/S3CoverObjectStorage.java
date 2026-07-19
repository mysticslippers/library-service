package me.ifmo.backend.catalog.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.shared.error.ObjectStorageException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3CoverObjectStorage implements CoverObjectStorage {

    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final CoverStorageProperties properties;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    @Override
    public StoredCoverMetadata put(String objectKey, byte[] content, String contentType) {
        ensureBucketAvailable();

        try {
            var request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .cacheControl(IMMUTABLE_CACHE_CONTROL)
                    .build();
            var response = s3Client.putObject(request, RequestBody.fromBytes(content));
            return new StoredCoverMetadata(content.length, contentType, response.eTag());
        } catch (RuntimeException exception) {
            throw unavailable("upload cover", exception);
        }
    }

    @Override
    public StoredCover get(String objectKey) {
        ensureBucketAvailable();

        try {
            var request = GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build();
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            GetObjectResponse metadata = response.response();
            return new StoredCover(
                    response,
                    metadata.contentLength(),
                    metadata.contentType(),
                    metadata.eTag(),
                    metadata.lastModified()
            );
        } catch (RuntimeException exception) {
            throw unavailable("read cover", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null)
            return;

        ensureBucketAvailable();

        try {
            var request = DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(request);
        } catch (RuntimeException exception) {
            throw unavailable("delete cover", exception);
        }
    }

    @Override
    public void verifyAvailable() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
            bucketReady.set(true);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404 && properties.createBucket()) {
                createBucket();
                return;
            }
            bucketReady.set(false);
            throw unavailable("check cover bucket", exception);
        } catch (RuntimeException exception) {
            bucketReady.set(false);
            throw unavailable("check cover bucket", exception);
        }
    }

    private void ensureBucketAvailable() {
        if (bucketReady.get())
            return;

        synchronized (bucketReady) {
            if (bucketReady.get())
                return;

            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
                bucketReady.set(true);
            } catch (S3Exception exception) {
                if (exception.statusCode() == 404 && properties.createBucket()) {
                    createBucket();
                    return;
                }
                throw unavailable("access cover bucket", exception);
            } catch (RuntimeException exception) {
                throw unavailable("access cover bucket", exception);
            }
        }
    }

    private void createBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
            bucketReady.set(true);
            log.info("Created S3 cover bucket '{}'", properties.bucket());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 409) {
                bucketReady.set(true);
                return;
            }
            throw unavailable("create cover bucket", exception);
        } catch (RuntimeException exception) {
            throw unavailable("create cover bucket", exception);
        }
    }

    private ObjectStorageException unavailable(String operation, RuntimeException cause) {
        log.error("Unable to {} in S3 bucket '{}'", operation, properties.bucket(), cause);
        return new ObjectStorageException("Cover storage is temporarily unavailable", cause);
    }
}
