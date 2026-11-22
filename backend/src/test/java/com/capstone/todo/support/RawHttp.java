package com.capstone.todo.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Raw-socket HTTP/1.1 client for requests the JDK {@code HttpClient} will not send: exact header bytes,
 * no or duplicate Content-Type, chunked bodies, {@code Expect: 100-continue}, a declared Content-Length
 * without the body, lowercase or unusual methods, and raw targets such as {@code /todos/a%2Fb}.
 *
 * <p>Every request is sent to 127.0.0.1 and, unless the test sets its own, with {@code Host} and
 * {@code Connection: close}. Headers are written exactly as given, in the order given.
 */
public final class RawHttp {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final int INTERIM_TIMEOUT_MILLIS = 2_000;
    private static final byte[] HEAD_END = {'\r', '\n', '\r', '\n'};

    private RawHttp() {
    }

    public static Request request(String method, String target) {
        return new Request(method, target);
    }

    /** A parsed response. Header names keep the case the server used. */
    public record Response(int status, String reason, List<String[]> headers, byte[] body) {

        /** First value of the header (name compared ignoring case), or null. */
        public String header(String name) {
            for (String[] h : headers) {
                if (h[0].equalsIgnoreCase(name)) {
                    return h[1];
                }
            }
            return null;
        }

        /** All values of the header, in the order received. */
        public List<String> headers(String name) {
            List<String> values = new ArrayList<>();
            for (String[] h : headers) {
                if (h[0].equalsIgnoreCase(name)) {
                    values.add(h[1]);
                }
            }
            return values;
        }

        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    public static final class Request {

        private final String method;
        private final String target;
        private final List<String[]> headers = new ArrayList<>();
        private byte[] body = new byte[0];
        private boolean chunked;
        private boolean expectContinue;

        private Request(String method, String target) {
            this.method = method;
            this.target = target;
        }

        /** Appends one header line; call again with the same name to send it twice. */
        public Request header(String name, String value) {
            headers.add(new String[] {name, value});
            return this;
        }

        /** Sets the body; Content-Length is added unless the test sets one itself. */
        public Request body(byte[] bytes) {
            this.body = bytes;
            return this;
        }

        public Request body(String text) {
            return body(text.getBytes(StandardCharsets.UTF_8));
        }

        /** Sends the given chunks with {@code Transfer-Encoding: chunked} and no Content-Length. */
        public Request chunked(byte[]... chunks) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] chunk : chunks) {
                if (chunk.length == 0) {
                    continue;
                }
                out.writeBytes((Integer.toHexString(chunk.length) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                out.writeBytes(chunk);
                out.writeBytes("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            }
            out.writeBytes("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            this.body = out.toByteArray();
            this.chunked = true;
            return this;
        }

        /** Adds {@code Expect: 100-continue} and sends the body only after the server's interim response. */
        public Request expectContinue() {
            this.expectContinue = true;
            return this;
        }

        public Response send(int port) {
            boolean head = method.equalsIgnoreCase("HEAD");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port),
                        CONNECT_TIMEOUT_MILLIS);
                socket.setSoTimeout(READ_TIMEOUT_MILLIS);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                ByteArrayOutputStream received = new ByteArrayOutputStream();

                out.write(headBytes(port));
                out.flush();
                boolean sendBody = body.length > 0;
                if (expectContinue) {
                    socket.setSoTimeout(INTERIM_TIMEOUT_MILLIS);
                    sendBody = awaitInterim(in, received) && sendBody;
                    socket.setSoTimeout(READ_TIMEOUT_MILLIS);
                }
                if (sendBody) {
                    try {
                        out.write(body);
                        out.flush();
                    } catch (IOException earlyClose) {
                        // The server may answer and close before reading the whole body (for example 413).
                        // The response, if any, is still read below.
                    }
                }
                return readResponse(in, received, head);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private byte[] headBytes(int port) {
            StringBuilder sb = new StringBuilder();
            sb.append(method).append(' ').append(target).append(" HTTP/1.1\r\n");
            boolean hasHost = false;
            boolean hasLength = false;
            boolean hasConnection = false;
            for (String[] h : headers) {
                String lower = h[0].toLowerCase(Locale.ROOT);
                hasHost |= lower.equals("host");
                hasLength |= lower.equals("content-length");
                hasConnection |= lower.equals("connection");
            }
            if (!hasHost) {
                sb.append("Host: 127.0.0.1:").append(port).append("\r\n");
            }
            if (!hasConnection) {
                sb.append("Connection: close\r\n");
            }
            if (chunked) {
                sb.append("Transfer-Encoding: chunked\r\n");
            } else if (!hasLength && body.length > 0) {
                sb.append("Content-Length: ").append(body.length).append("\r\n");
            }
            if (expectContinue) {
                sb.append("Expect: 100-continue\r\n");
            }
            for (String[] h : headers) {
                sb.append(h[0]).append(": ").append(h[1]).append("\r\n");
            }
            sb.append("\r\n");
            return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        }

        /**
         * Reads until the first response head is complete. Returns true (body should be sent) if it was an
         * interim 100 (and drops it from {@code received}) or if nothing arrived within the interim timeout
         * (clients then send the body anyway); returns false if a final response came first.
         */
        private static boolean awaitInterim(InputStream in, ByteArrayOutputStream received) throws IOException {
            byte[] buffer = new byte[4096];
            try {
                while (indexOf(received.toByteArray(), HEAD_END, 0) < 0) {
                    int n = in.read(buffer);
                    if (n < 0) {
                        return false;
                    }
                    received.write(buffer, 0, n);
                }
            } catch (SocketTimeoutException noInterim) {
                return true;
            }
            byte[] data = received.toByteArray();
            int end = indexOf(data, HEAD_END, 0) + HEAD_END.length;
            String statusLine = new String(data, 0, Math.min(data.length, 12), StandardCharsets.ISO_8859_1);
            if (statusLine.startsWith("HTTP/1.1 100")) {
                received.reset();
                received.write(data, end, data.length - end);
                return true;
            }
            return false;
        }

        private static Response readResponse(InputStream in, ByteArrayOutputStream received, boolean head)
                throws IOException {
            byte[] buffer = new byte[8192];
            boolean eof = false;
            while (true) {
                Response parsed = parse(received.toByteArray(), head, eof);
                if (parsed != null) {
                    return parsed;
                }
                try {
                    int n = in.read(buffer);
                    if (n < 0) {
                        eof = true;
                    } else {
                        received.write(buffer, 0, n);
                    }
                } catch (SocketTimeoutException timeout) {
                    throw new IOException("timed out waiting for the response; received "
                            + received.size() + " bytes", timeout);
                } catch (IOException reset) {
                    if (received.size() == 0) {
                        throw reset;
                    }
                    eof = true;
                }
            }
        }
    }

    /** Parses a response from the bytes received so far; null if more bytes are needed and the stream is open. */
    static Response parse(byte[] data, boolean head, boolean eof) throws IOException {
        int headEnd = indexOf(data, HEAD_END, 0);
        if (headEnd < 0) {
            if (eof) {
                throw new IOException("connection closed before a complete response head; received "
                        + data.length + " bytes");
            }
            return null;
        }
        String[] lines = new String(data, 0, headEnd, StandardCharsets.ISO_8859_1).split("\r\n");
        String[] status = lines[0].split(" ", 3);
        int code = Integer.parseInt(status[1]);
        String reason = status.length > 2 ? status[2] : "";
        List<String[]> headers = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            headers.add(new String[] {lines[i].substring(0, colon), lines[i].substring(colon + 1).trim()});
        }
        int bodyStart = headEnd + HEAD_END.length;
        Response shell = new Response(code, reason, headers, new byte[0]);
        if (head || code < 200 || code == 204 || code == 304) {
            return shell;
        }
        String transfer = shell.header("Transfer-Encoding");
        if (transfer != null && transfer.toLowerCase(Locale.ROOT).contains("chunked")) {
            byte[] decoded = decodeChunked(data, bodyStart);
            if (decoded == null) {
                return eof ? new Response(code, reason, headers, new byte[0]) : null;
            }
            return new Response(code, reason, headers, decoded);
        }
        String length = shell.header("Content-Length");
        if (length != null) {
            int n = Integer.parseInt(length.trim());
            if (data.length - bodyStart >= n) {
                return new Response(code, reason, headers, slice(data, bodyStart, bodyStart + n));
            }
            return eof ? new Response(code, reason, headers, slice(data, bodyStart, data.length)) : null;
        }
        return eof ? new Response(code, reason, headers, slice(data, bodyStart, data.length)) : null;
    }

    /** Decodes a chunked body starting at {@code start}; null if it is not complete yet. */
    private static byte[] decodeChunked(byte[] data, int start) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int pos = start;
        while (true) {
            int lineEnd = indexOf(data, new byte[] {'\r', '\n'}, pos);
            if (lineEnd < 0) {
                return null;
            }
            String sizeLine = new String(data, pos, lineEnd - pos, StandardCharsets.ISO_8859_1);
            int semicolon = sizeLine.indexOf(';');
            int size = Integer.parseInt((semicolon < 0 ? sizeLine : sizeLine.substring(0, semicolon)).trim(), 16);
            pos = lineEnd + 2;
            if (size == 0) {
                if (data.length >= pos + 2 && data[pos] == '\r' && data[pos + 1] == '\n') {
                    return body.toByteArray();
                }
                return indexOf(data, HEAD_END, lineEnd) >= 0 ? body.toByteArray() : null;
            }
            if (data.length < pos + size + 2) {
                return null;
            }
            body.write(data, pos, size);
            pos += size + 2;
        }
    }

    private static byte[] slice(byte[] data, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(data, from, out, 0, out.length);
        return out;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
