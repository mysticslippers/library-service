package me.ifmo.backend.catalog.application.cover;

import me.ifmo.backend.catalog.storage.CoverStorageProperties;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.PayloadTooLargeException;
import me.ifmo.backend.shared.error.UnsupportedCoverFormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cover image validator")
class CoverImageValidatorTest {

    private final CoverImageValidator validator = new CoverImageValidator(properties(DataSize.ofMegabytes(5)));

    @Test
    @DisplayName("detects PNG by signature instead of declared content type")
    void detectsPngBySignature() {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
        };
        var file = new MockMultipartFile("file", "cover.txt", "text/plain", png);

        var result = validator.validate(file);

        assertThat(result.content()).isEqualTo(png);
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
    }

    @Test
    @DisplayName("accepts JPEG and normalizes its media type")
    void acceptsJpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        var file = new MockMultipartFile("file", "cover.jpeg", "image/pjpeg", jpeg);

        var result = validator.validate(file);

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo("jpg");
    }

    @Test
    @DisplayName("rejects content that only claims to be an image")
    void rejectsSpoofedContentType() {
        var file = new MockMultipartFile(
                "file", "cover.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(UnsupportedCoverFormatException.class);
    }

    @Test
    @DisplayName("rejects a cover larger than the configured limit")
    void rejectsOversizedCover() {
        var smallValidator = new CoverImageValidator(properties(DataSize.ofBytes(3)));
        var file = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01});

        assertThatThrownBy(() -> smallValidator.validate(file))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    @DisplayName("rejects an empty upload")
    void rejectsEmptyUpload() {
        var file = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must not be empty");
    }

    private CoverStorageProperties properties(DataSize maxFileSize) {
        return new CoverStorageProperties(
                "http://localhost:9000",
                "us-east-1",
                "access-key",
                "secret-key",
                "library-covers",
                true,
                true,
                Duration.ofSeconds(10),
                maxFileSize
        );
    }
}
