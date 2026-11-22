package com.capstone.todo.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP/1.1 client helpers over the JDK {@code HttpClient}. The JDK client refuses to set restricted headers
 * (Host, Content-Length, Connection, Expect); use {@link RawHttp} for those cases.
 */
public final class TestClients {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private TestClients() {
    }

    /** A client pinned to HTTP/1.1 that never follows redirects. */
    public static HttpClient http11() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Sends one request to 127.0.0.1.
     *
     * @param rawPathAndQuery sent as given, for example {@code /todos/abc%2Fdef?x=1}
     * @param body            request body, or null for none
     * @param headers         alternating header names and values
     */
    public static HttpResponse<byte[]> send(HttpClient client, String method, int port, String rawPathAndQuery,
            byte[] body, String... headers) {
        if (headers.length % 2 != 0) {
            throw new IllegalArgumentException("headers must be name/value pairs");
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + rawPathAndQuery))
                .timeout(TIMEOUT)
                .method(method, publisher);
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the response", e);
        }
    }

    public static HttpResponse<byte[]> get(HttpClient client, int port, String rawPathAndQuery) {
        return send(client, "GET", port, rawPathAndQuery, null);
    }
}
