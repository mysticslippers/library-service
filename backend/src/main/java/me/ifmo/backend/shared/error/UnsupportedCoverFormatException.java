package me.ifmo.backend.shared.error;

import org.springframework.http.HttpStatus;

public class UnsupportedCoverFormatException extends ApiException {

    public UnsupportedCoverFormatException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_COVER_FORMAT", message);
    }
}
