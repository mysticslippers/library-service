package me.ifmo.backend.authentication.web.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.IOException;

final class BoundedServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final int maximumBytes;
    private int readBytes;

    BoundedServletInputStream(ServletInputStream delegate, int maximumBytes) {
        this.delegate = delegate;
        this.maximumBytes = maximumBytes;
    }

    @Override
    public int read() throws IOException {
        int value = delegate.read();
        recordRead(value < 0 ? 0 : 1);
        return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        int boundedLength = Math.min(length, maximumBytes - readBytes + 1);
        int count = delegate.read(bytes, offset, boundedLength);
        recordRead(count);
        return count;
    }

    @Override
    public boolean isFinished() {
        return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
        delegate.setReadListener(listener);
    }

    private void recordRead(int count) throws IOException {
        if (count <= 0)
            return;

        readBytes += count;
        if (readBytes > maximumBytes)
            throw new IOException("Authentication request is too large");
    }
}
