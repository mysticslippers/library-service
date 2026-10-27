package me.ifmo.backend.authentication.web.filter;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class BoundedAuthRequest extends HttpServletRequestWrapper {

    private final int maximumBytes;
    private ServletInputStream input;

    BoundedAuthRequest(HttpServletRequest request, int maximumBytes) {
        super(request);
        this.maximumBytes = maximumBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (input == null)
            input = new BoundedServletInputStream(super.getInputStream(), maximumBytes);

        return input;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
