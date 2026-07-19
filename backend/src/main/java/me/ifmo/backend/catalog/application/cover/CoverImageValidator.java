package me.ifmo.backend.catalog.application.cover;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.catalog.storage.CoverStorageProperties;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.PayloadTooLargeException;
import me.ifmo.backend.shared.error.UnsupportedCoverFormatException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CoverImageValidator {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final CoverStorageProperties properties;

    public ValidatedCover validate(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new BusinessRuleException("Cover file must not be empty");

        long maxSize = properties.maxFileSize().toBytes();
        if (file.getSize() > maxSize)
            throw tooLarge(maxSize);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessRuleException("Unable to read cover file");
        }

        if (content.length == 0)
            throw new BusinessRuleException("Cover file must not be empty");

        if (content.length > maxSize)
            throw tooLarge(maxSize);

        if (isJpeg(content))
            return new ValidatedCover(content, "image/jpeg", "jpg");

        if (startsWith(content, PNG_SIGNATURE))
            return new ValidatedCover(content, "image/png", "png");

        if (isWebp(content))
            return new ValidatedCover(content, "image/webp", "webp");

        throw new UnsupportedCoverFormatException("Only JPEG, PNG and WebP cover images are supported");
    }

    private PayloadTooLargeException tooLarge(long maxSize) {
        return new PayloadTooLargeException(
                "Cover file exceeds the maximum size of %d bytes".formatted(maxSize));
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && content[0] == (byte) 0xFF
                && content[1] == (byte) 0xD8
                && content[2] == (byte) 0xFF;
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12
                && asciiEquals(content, 0, "RIFF")
                && asciiEquals(content, 8, "WEBP");
    }

    private boolean asciiEquals(byte[] content, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset + bytes.length > content.length)
            return false;

        for (int index = 0; index < bytes.length; index++) {
            if (content[offset + index] != bytes[index])
                return false;
        }
        return true;
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length)
            return false;

        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index])
                return false;
        }
        return true;
    }
}
