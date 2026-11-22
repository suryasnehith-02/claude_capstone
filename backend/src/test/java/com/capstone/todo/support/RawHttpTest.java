package com.capstone.todo.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/** Checks the test helpers against a one-connection stub server, so T-12 can trust what they send and parse. */
class RawHttpTest {

    /** Accepts one connection, optionally sends an interim response, reads the request, sends {@code reply}. */
    private static final class Stub implements AutoCloseable {

        private final ServerSocket server;
        private final CompletableFuture<byte[]> request = new CompletableFuture<>();

        Stub(byte[] reply, boolean interim) throws IOException {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            Thread.ofVirtual().start(() -> serve(reply, interim));
        }

        int port() {
            return server.getLocalPort();
        }

        byte[] receivedRequest() {
            return request.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).join();
        }

        private void serve(byte[] reply, boolean interim) {
            try (Socket socket = server.accept()) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                ByteArrayOutputStream received = new ByteArrayOutputStream();
                int length = readHead(in, received);
                if (interim) {
                    out.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                }
                readBody(in, received, length);
                request.complete(received.toByteArray());
                out.write(reply);
                out.flush();
            } catch (IOException e) {
                request.completeExceptionally(e);
            }
        }

        private static int readHead(InputStream in, ByteArrayOutputStream received) throws IOException {
            while (!new String(received.toByteArray(), StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                received.write(b);
            }
            String head = received.toString(StandardCharsets.ISO_8859_1);
            for (String line : head.split("\r\n")) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    return Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
            }
            return 0;
        }

        private static void readBody(InputStream in, ByteArrayOutputStream received, int length) throws IOException {
            for (int i = 0; i < length; i++) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                received.write(b);
            }
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void sendsExactHeadersIncludingLowercaseMethodAndTwoContentTypeHeaders() throws Exception {
        try (Stub stub = new Stub(bytes("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"), false)) {
            RawHttp.Response response = RawHttp.request("post", "/todos/a%2Fb?x=1")
                    .header("Content-Type", "application/json")
                    .header("Content-Type", "text/plain")
                    .body("{}")
                    .send(stub.port());

            String request = new String(stub.receivedRequest(), StandardCharsets.ISO_8859_1);
            assertThat(request).startsWith("post /todos/a%2Fb?x=1 HTTP/1.1\r\n");
            assertThat(request).contains("Host: 127.0.0.1:" + stub.port() + "\r\n");
            assertThat(request).contains("Connection: close\r\n");
            assertThat(request).contains("Content-Length: 2\r\n");
            assertThat(request).contains("Content-Type: application/json\r\nContent-Type: text/plain\r\n");
            assertThat(request).endsWith("\r\n\r\n{}");
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.reason()).isEqualTo("OK");
            assertThat(response.bodyText()).isEqualTo("ok");
        }
    }

    @Test
    void sendsNoContentTypeAndKeepsADeclaredContentLengthWithoutAddingAnother() throws Exception {
        try (Stub stub = new Stub(bytes("HTTP/1.1 413 Payload Too Large\r\nContent-Length: 0\r\n\r\n"), false)) {
            RawHttp.Response response = RawHttp.request("PUT", "/todos/x")
                    .header("Content-Length", "0")
                    .send(stub.port());

            String request = new String(stub.receivedRequest(), StandardCharsets.ISO_8859_1);
            assertThat(request).doesNotContain("Content-Type");
            assertThat(request.split("Content-Length", -1)).hasSize(2);
            assertThat(response.status()).isEqualTo(413);
        }
    }

    @Test
    void encodesChunkedRequestsAndDecodesChunkedResponses() throws Exception {
        byte[] reply = bytes("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "3\r\nabc\r\n4;ext=1\r\ndefg\r\n0\r\n\r\n");
        try (Stub stub = new Stub(reply, false)) {
            RawHttp.Response response = RawHttp.request("PATCH", "/todos/x/status")
                    .chunked(bytes("ab"), bytes("cde"))
                    .send(stub.port());

            assertThat(response.bodyText()).isEqualTo("abcdefg");
            assertThat(response.header("transfer-encoding")).isEqualTo("chunked");
        }
    }

    @Test
    void headResponsesHaveNoBodyEvenWithAContentLength() throws Exception {
        try (Stub stub = new Stub(bytes("HTTP/1.1 405 Method Not Allowed\r\nAllow: GET, POST\r\n"
                + "Allow: PUT\r\nContent-Length: 40\r\n\r\n"), false)) {
            RawHttp.Response response = RawHttp.request("HEAD", "/todos").send(stub.port());

            assertThat(response.status()).isEqualTo(405);
            assertThat(response.body()).isEmpty();
            assertThat(response.headers("ALLOW")).containsExactly("GET, POST", "PUT");
        }
    }

    @Test
    void sendsTheBodyOnlyAfterTheInterimResponseWhenExpectContinueIsSet() throws Exception {
        try (Stub stub = new Stub(bytes("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n"), true)) {
            RawHttp.Response response = RawHttp.request("POST", "/todos")
                    .expectContinue()
                    .body("{\"title\":\"a\"}")
                    .send(stub.port());

            String request = new String(stub.receivedRequest(), StandardCharsets.ISO_8859_1);
            assertThat(request).contains("Expect: 100-continue\r\n");
            assertThat(request).endsWith("{\"title\":\"a\"}");
            assertThat(response.status()).isEqualTo(201);
        }
    }

    @Test
    void theJdkClientHelperTalksHttp11AndReturnsBytes() throws Exception {
        try (Stub stub = new Stub(bytes("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nhi"), false);
                HttpClient client = TestClients.http11()) {
            HttpResponse<byte[]> response = TestClients.get(client, stub.port(), "/todos?limit=1");

            String request = new String(stub.receivedRequest(), StandardCharsets.ISO_8859_1);
            assertThat(request).startsWith("GET /todos?limit=1 HTTP/1.1\r\n");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("hi");
        }
    }
}
