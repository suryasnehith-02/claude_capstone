package com.capstone.todo.support;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.ServletInputStream;

/**
 * A {@link MockHttpServletRequest} for the gate and precedence unit tests. Three things the plain mock cannot do:
 * the declared Content-Length is independent of the body stream (so a chunked body is a declared length of -1 and an
 * understated length can be modelled), the body stream counts how many bytes were read and whether it was opened at
 * all (so "unread" is asserted, not assumed), and every Content-Type header line is kept.
 *
 * <p>The last point is why the headers are held here: {@link MockHttpServletRequest#addHeader} treats Content-Type as
 * the single content type and keeps only the last value. Two real header lines on real Tomcat are for the contract
 * tests of T-12.
 */
public final class GateRequest extends MockHttpServletRequest {

    private long declaredLength = -1;
    private InputStream source = InputStream.nullInputStream();
    private long consumed;
    private boolean streamRequested;
    private final List<String> contentTypeLines = new ArrayList<>();

    public GateRequest(String method, String requestUri) {
        super(method, requestUri);
    }

    /** The Content-Length the request claims; -1 for none (a chunked body). */
    public GateRequest declaring(long length) {
        this.declaredLength = length;
        return this;
    }

    public GateRequest body(byte[] bytes) {
        this.source = new ByteArrayInputStream(bytes);
        return this;
    }

    public GateRequest bodyFrom(InputStream stream) {
        this.source = stream;
        return this;
    }

    /** One Content-Type header line per value. */
    public GateRequest contentTypes(String... values) {
        contentTypeLines.addAll(Arrays.asList(values));
        return this;
    }

    /** Bytes the code under test has taken from the body stream. */
    public long consumed() {
        return consumed;
    }

    /** Whether the code under test ever asked for the body stream. */
    public boolean streamRequested() {
        return streamRequested;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if ("Content-Type".equalsIgnoreCase(name)) {
            return Collections.enumeration(contentTypeLines);
        }
        return super.getHeaders(name);
    }

    @Override
    public long getContentLengthLong() {
        return declaredLength;
    }

    @Override
    public int getContentLength() {
        return (int) Math.min(declaredLength, Integer.MAX_VALUE);
    }

    @Override
    public ServletInputStream getInputStream() {
        streamRequested = true;
        return new DelegatingServletInputStream(new FilterInputStream(source) {
            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value >= 0) {
                    consumed++;
                }
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int count = super.read(buffer, offset, length);
                if (count > 0) {
                    consumed += count;
                }
                return count;
            }
        });
    }

    /** An endless body of spaces: reading it all would never end. */
    public static final class Endless extends InputStream {
        @Override
        public int read() {
            return ' ';
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            Arrays.fill(buffer, offset, offset + length, (byte) ' ');
            return length;
        }
    }
}
