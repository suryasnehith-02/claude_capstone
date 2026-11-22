package com.capstone.todo.web.gate;

import java.util.Objects;

/**
 * The request body as {@code BodyGate} read it, before any JSON is looked at.
 *
 * @param bytes   the body bytes; empty when there are none or when the method's body is never read
 * @param present whether a body is present for the media-type rule (D-4): Content-Length above 0, or at least one
 *                byte read. When false, stage P3 was skipped and the empty bytes go to P5, which rejects them.
 */
public record RawBody(byte[] bytes, boolean present) {

    private static final byte[] NO_BYTES = new byte[0];

    public RawBody {
        Objects.requireNonNull(bytes, "bytes");
    }

    /** No body, as for GET and DELETE or a POST with no bytes. */
    public static RawBody absent() {
        return new RawBody(NO_BYTES, false);
    }

    /** Prints only the size: the body is client data and must not reach a log line. */
    @Override
    public String toString() {
        return "RawBody[" + bytes.length + " bytes, present=" + present + "]";
    }
}
