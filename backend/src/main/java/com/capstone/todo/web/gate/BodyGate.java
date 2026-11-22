package com.capstone.todo.web.gate;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Stages P2 and P3 (architecture D-2, D-3, D-4): the body size limit and the media type, for POST, PUT and PATCH
 * only. The body of any other method is never read (A-8).
 *
 * <p>P2: a declared Content-Length above {@value #MAX_BODY_BYTES} gives PAYLOAD_TOO_LARGE without reading a byte.
 * Otherwise the body is read with a hard cap of {@code MAX_BODY_BYTES + 1} bytes, whatever the Content-Length says
 * (a chunked body has none), and reaching the cap gives PAYLOAD_TOO_LARGE. A body of exactly the limit is accepted.
 *
 * <p>P3 (only when a body is present): there must be exactly one Content-Type header and, read with
 * the part before the first {@code ;}, trimmed, must be {@code application/json} in any letter case. Every parameter
 * is ignored, charset included, and a charset name is never resolved (decision S2-4: the body is always UTF-8). A
 * missing header, two headers, a value without a valid type/subtype, {@code application/merge-patch+json}, form and
 * multipart types all give UNSUPPORTED_MEDIA_TYPE. A body is
 * present when Content-Length is above 0 or at least one byte was read, so {@code Content-Length: 0} without a type
 * skips P3 and the empty body is rejected later as INVALID_REQUEST_BODY (A-11).
 *
 * <p>Nothing here touches storage, and the checks never put request values in a message.
 */
public final class BodyGate {

    /** The largest body accepted, in bytes (D-2). */
    public static final int MAX_BODY_BYTES = 65_536;

    private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    /**
     * @return the body and whether it counts as present; {@link RawBody#absent()} for a method whose body is ignored
     * @throws ApiException PAYLOAD_TOO_LARGE (P2) or UNSUPPORTED_MEDIA_TYPE (P3)
     * @throws IOException  when the container fails to deliver the body (for example the client went away)
     */
    public RawBody check(HttpServletRequest request) throws IOException {
        if (!METHODS_WITH_BODY.contains(request.getMethod())) {
            return RawBody.absent();
        }
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > MAX_BODY_BYTES) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        boolean present = declaredLength > 0 || bytes.length > 0;
        if (!present) {
            return RawBody.absent();
        }
        requireJson(request);
        return new RawBody(bytes, true);
    }

    private static void requireJson(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(CONTENT_TYPE);
        if (values == null || !values.hasMoreElements()) {
            throw unsupported();
        }
        String value = values.nextElement();
        if (values.hasMoreElements() || value == null) {
            throw unsupported();
        }
        // Decision S2-4: every parameter (charset included) is ignored, so only the text before the first ';' counts.
        // A value without a valid type/subtype can never equal application/json, so it is rejected by the same test.
        int parameters = value.indexOf(';');
        String mediaType = (parameters < 0 ? value : value.substring(0, parameters)).trim();
        if (!APPLICATION_JSON.equals(mediaType.toLowerCase(Locale.ROOT))) {
            throw unsupported();
        }
    }

    private static ApiException unsupported() {
        return new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }
}
