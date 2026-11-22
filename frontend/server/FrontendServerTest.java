/*
 * CAP-1 black-box test of FrontendServer.java (static files and the /todos proxy).
 *
 * Run with no arguments, from frontend/ or from the repository root (JDK 21+, no dependency):
 *   java server/FrontendServerTest.java
 *
 * It starts an in-process stub backend on a free port and FrontendServer.java as a child JVM, sends
 * requests, and prints PASS, FAIL or SKIPPED per case and a final "N passed, M failed, K skipped" line.
 * Exit code: 0 when no case failed, 1 when a case failed, 2 when frontend/ cannot be found.
 * Functional checks only. It never creates or changes files.
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class FrontendServerTest {

    private static final Duration READY_WAIT = Duration.ofSeconds(60);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long WATCHDOG_SECONDS = 120;

    private static final String[] METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE"};
    private static final String[] PATHS = {"/todos", "/todos/abc-123", "/todos/abc-123/status"};
    private static final String QUERY = "q=a%20b&z=%C3%A9";
    private static final String JSON = "application/json";
    // Multi-byte UTF-8 text, built from code points so the source file stays ASCII.
    private static final String NON_ASCII = "caf" + (char) 0xE9 + " " + (char) 0x2713;

    private static volatile Process child;
    private static int frontendPort;
    private static int passed;
    private static int failed;
    private static int skipped;

    public static void main(String[] args) {
        startWatchdog();

        Path frontendDir = findFrontendDir();
        if (frontendDir == null) {
            System.out.println("FAIL cannot find frontend/: run from frontend/ or from the repository root");
            System.exit(2);
            return;
        }
        boolean hasIndex = Files.isRegularFile(frontendDir.resolve("public/index.html"));

        Stub stub = null;
        StringBuffer output = new StringBuffer();
        try {
            stub = new Stub();
            boolean serverUp = hasIndex && startServer(frontendDir, stub.port(), output);
            runCases(buildCases(frontendDir, stub, hasIndex, output), serverUp, output);
        } catch (Exception e) {
            fail("test setup", e.toString());
        } finally {
            if (stub != null) {
                stub.stop();
            }
            stopChild();
        }

        System.out.println(passed + " passed, " + failed + " failed, " + skipped + " skipped");
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(WATCHDOG_SECONDS * 1000);
            } catch (InterruptedException e) {
                return;
            }
            System.out.println("FAIL watchdog: the run took longer than " + WATCHDOG_SECONDS + " s");
            Process p = child;
            if (p != null) {
                p.destroyForcibly();
            }
            Runtime.getRuntime().halt(1);
        });
        watchdog.setDaemon(true);
        watchdog.start();
    }

    // ---- cases -------------------------------------------------------------------------------

    private interface Body {
        void run() throws Exception;
    }

    private record Case(String name, boolean needsServer, BooleanSupplier skipWhen, String skipReason, Body body) {
    }

    private record Scripted(String label, int status, String type, String body) {
    }

    private static List<Case> buildCases(Path dir, Stub stub, boolean hasIndex, StringBuffer output) {
        List<Case> cases = new ArrayList<>();
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

        // The server refuses to start without public/index.html. This is the only case that can run
        // before the UI files exist.
        cases.add(new Case("server refuses to start without public/index.html", false,
                () -> hasIndex, "public/index.html exists", () -> {
                    Process p = launch(dir, 65000, stub.port(), output, new CountDownLatch(1));
                    boolean exited = p.waitFor(60, TimeUnit.SECONDS);
                    expect(exited, "the server did not exit");
                    expect(p.exitValue() != 0, "exit code was 0");
                    String message = "run from the frontend/ directory";
                    for (int i = 0; i < 20 && !output.toString().contains(message); i++) {
                        Thread.sleep(100);
                    }
                    expect(output.toString().contains(message), "message missing, output: " + output);
                }));

        // CR-1: the same server file also finds public/ when started from the repository root.
        cases.add(new Case("server started from the repository root finds frontend/public", false,
                () -> !hasIndex || dir.getParent() == null, "public/index.html missing", () -> {
                    int port;
                    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                        port = socket.getLocalPort();
                    }
                    StringBuffer rootOutput = new StringBuffer();
                    CountDownLatch ready = new CountDownLatch(1);
                    Process previous = child;
                    Process p = launch(dir.getParent(), dir.getFileName() + "/server/FrontendServer.java", port,
                            stub.port(), rootOutput, ready);
                    try {
                        expect(ready.await(READY_WAIT.toSeconds(), TimeUnit.SECONDS),
                                "no start-up line from the repository root, output: " + rootOutput);
                        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                                .timeout(REQUEST_TIMEOUT).GET().build();
                        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                        expect(response.statusCode() == 200, "status " + response.statusCode());
                        expect(response.body().contains("<html"), "body is not the index page");
                    } finally {
                        p.destroyForcibly();
                        p.waitFor(10, TimeUnit.SECONDS);
                        child = previous;
                    }
                }));

        cases.add(new Case("static GET / returns text/html", true, null, null, () -> {
            Resp r = send(http, "GET", "/", null, null);
            expect(r.status() == 200, "status " + r.status());
            expect(r.contentType() != null && r.contentType().startsWith("text/html"), "type " + r.contentType());
        }));
        cases.add(new Case("static GET /js/main.js returns text/javascript", true,
                () -> !Files.isRegularFile(dir.resolve("public/js/main.js")), "file not yet created", () -> {
                    Resp r = send(http, "GET", "/js/main.js", null, null);
                    expect(r.status() == 200, "status " + r.status());
                    expect(r.contentType() != null && r.contentType().startsWith("text/javascript"),
                            "type " + r.contentType());
                }));
        cases.add(new Case("static GET /tests redirects to /tests/", true,
                () -> !Files.isDirectory(dir.resolve("public/tests")), "directory not yet created", () -> {
                    Resp r = send(http, "GET", "/tests", null, null);
                    expect(r.status() == 301, "status " + r.status());
                    expect("/tests/".equals(r.header("Location")), "Location " + r.header("Location"));
                }));
        cases.add(new Case("static missing file returns 404", true, null, null, () -> {
            Resp r = send(http, "GET", "/no-such-file.txt", null, null);
            expect(r.status() == 404, "status " + r.status());
        }));
        cases.add(new Case("static POST returns 405", true, null, null, () -> {
            Resp r = send(http, "POST", "/index.html", null, null);
            expect(r.status() == 405, "status " + r.status());
        }));
        cases.add(new Case("/todosx is not proxied", true, null, null, () -> {
            int before = stub.seen.size();
            Resp r = send(http, "GET", "/todosx", null, null);
            expect(r.status() == 404, "status " + r.status());
            expect(stub.seen.size() == before, "the stub received a request");
        }));

        // Every method on every path reaches the stub with the same method, raw path, query,
        // Content-Type and body bytes.
        for (String method : METHODS) {
            for (String path : PATHS) {
                cases.add(new Case("proxy " + method + " " + path, true, null, null, () -> {
                    stub.script(200, JSON, utf8("{\"data\":[]}"));
                    boolean withBody = method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
                    byte[] sent = withBody ? utf8("{\"title\":\"" + NON_ASCII + "\",\"description\":\"\"}") : null;
                    int before = stub.seen.size();
                    Resp r = send(http, method, path + "?" + QUERY, withBody ? JSON : null, sent);
                    expect(r.status() == 200, "status " + r.status());
                    expect(stub.seen.size() == before + 1, "the stub saw " + (stub.seen.size() - before) + " requests");
                    Seen seen = stub.seen.get(stub.seen.size() - 1);
                    expect(method.equals(seen.method()), "method " + seen.method());
                    expect(path.equals(seen.rawPath()), "path " + seen.rawPath());
                    expect(QUERY.equals(seen.rawQuery()), "query " + seen.rawQuery());
                    expect(withBody ? JSON.equals(seen.contentType()) : seen.contentType() == null,
                            "content type " + seen.contentType());
                    expect(Arrays.equals(withBody ? sent : new byte[0], seen.body()), "request body differs");
                }));
            }
        }

        // Scripted backend responses come back with status, content type and bytes unchanged.
        List<Scripted> scripted = List.of(
                new Scripted("200 json", 200, JSON, "{\"data\":{\"id\":\"1\",\"title\":\"" + NON_ASCII + "\"}}"),
                new Scripted("201 json", 201, JSON, "{\"data\":{\"id\":\"2\"}}"),
                new Scripted("204 no body", 204, null, ""),
                new Scripted("400 envelope", 400, JSON,
                        "{\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"x\",\"details\":[]}}"),
                new Scripted("404 envelope", 404, JSON,
                        "{\"error\":{\"code\":\"TODO_NOT_FOUND\",\"message\":\"x\"}}"),
                new Scripted("405 envelope", 405, JSON,
                        "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":\"x\"}}"),
                new Scripted("415 envelope", 415, JSON,
                        "{\"error\":{\"code\":\"UNSUPPORTED_MEDIA_TYPE\",\"message\":\"x\"}}"),
                new Scripted("500 envelope", 500, JSON,
                        "{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"x\"}}"),
                new Scripted("400 text/html", 400, "text/html;charset=utf-8",
                        "<!doctype html><html><body><h1>HTTP Status 400 - Bad Request</h1></body></html>"));
        for (Scripted s : scripted) {
            cases.add(new Case("proxy passes through " + s.label(), true, null, null, () -> {
                byte[] expected = utf8(s.body());
                stub.script(s.status(), s.type(), expected);
                Resp r = send(http, "GET", "/todos/abc-123", null, null);
                expect(r.status() == s.status(), "status " + r.status());
                expect(s.type() == null ? r.contentType() == null : s.type().equals(r.contentType()),
                        "content type " + r.contentType());
                expect(Arrays.equals(expected, r.body()), "body differs");
            }));
        }

        // Must stay last: it stops the stub.
        cases.add(new Case("proxy answers 502 BAD_GATEWAY when the backend is down", true, null, null, () -> {
            stub.stop();
            Resp r = send(http, "GET", "/todos", null, null);
            expect(r.status() == 502, "status " + r.status());
            expect(r.contentType() != null && r.contentType().startsWith(JSON), "type " + r.contentType());
            String text = new String(r.body(), StandardCharsets.UTF_8);
            expect(text.contains("\"code\":\"BAD_GATEWAY\""), "body " + text);
        }));
        return cases;
    }

    private static void runCases(List<Case> cases, boolean serverUp, StringBuffer output) {
        for (Case c : cases) {
            if (c.skipWhen() != null && c.skipWhen().getAsBoolean()) {
                skip(c.name(), c.skipReason());
            } else if (c.needsServer() && !serverUp) {
                skip(c.name(), "the server cannot start until public/index.html exists; UI files arrive in later waves");
            } else {
                try {
                    c.body().run();
                    System.out.println("PASS " + c.name());
                    passed++;
                } catch (Throwable e) {
                    fail(c.name(), e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        }
        if (failed > 0) {
            System.out.println("--- server output ---");
            System.out.println(output);
        }
    }

    // ---- server process ----------------------------------------------------------------------

    private static Path findFrontendDir() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.isRegularFile(cwd.resolve("server/FrontendServer.java"))) {
            return cwd;
        }
        Path sub = cwd.resolve("frontend");
        return Files.isRegularFile(sub.resolve("server/FrontendServer.java")) ? sub : null;
    }

    private static boolean startServer(Path dir, int backendPort, StringBuffer output) throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            frontendPort = socket.getLocalPort();
        }
        CountDownLatch ready = new CountDownLatch(1);
        Process p = launch(dir, frontendPort, backendPort, output, ready);
        long deadline = System.nanoTime() + READY_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (ready.await(200, TimeUnit.MILLISECONDS)) {
                return true;
            }
            if (!p.isAlive()) {
                break;
            }
        }
        fail("server start", "no start-up line within " + READY_WAIT.toSeconds() + " s; output: " + output);
        return false;
    }

    /** Starts FrontendServer.java as a child JVM and drains its output; counts the latch down on the start-up line. */
    private static Process launch(Path dir, int port, int backendPort, StringBuffer output, CountDownLatch ready)
            throws IOException {
        return launch(dir, "server/FrontendServer.java", port, backendPort, output, ready);
    }

    private static Process launch(Path workDir, String script, int port, int backendPort, StringBuffer output,
            CountDownLatch ready) throws IOException {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path java = Files.isRegularFile(bin.resolve("java.exe")) ? bin.resolve("java.exe") : bin.resolve("java");
        ProcessBuilder pb = new ProcessBuilder(java.toString(), script,
                "--port=" + port, "--backend-port=" + backendPort);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        child = p;
        String startLine = "Frontend at http://127.0.0.1:" + port + "/";
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    output.append(line).append('\n');
                    if (line.startsWith(startLine)) {
                        ready.countDown();
                    }
                }
            } catch (IOException ignored) {
                // the process ended
            }
        });
        reader.setDaemon(true);
        reader.start();
        return p;
    }

    private static void stopChild() {
        Process p = child;
        if (p == null) {
            return;
        }
        p.destroy();
        try {
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }

    // ---- stub backend ------------------------------------------------------------------------

    private record Seen(String method, String rawPath, String rawQuery, String contentType, byte[] body) {
    }

    private static final class Stub {
        final HttpServer server;
        final List<Seen> seen = new CopyOnWriteArrayList<>();
        private volatile int status = 200;
        private volatile String type = JSON;
        private volatile byte[] body = new byte[0];
        private volatile boolean stopped;

        Stub() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        void script(int status, String type, byte[] body) {
            this.status = status;
            this.type = type;
            this.body = body;
        }

        synchronized void stop() {
            if (!stopped) {
                stopped = true;
                server.stop(0);
            }
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                        exchange.getRequestURI().getRawQuery(),
                        exchange.getRequestHeaders().getFirst("Content-Type"), requestBody));
                if (type != null) {
                    exchange.getResponseHeaders().set("Content-Type", type);
                }
                byte[] out = body;
                if (status == 204 || out.length == 0) {
                    exchange.sendResponseHeaders(status, -1);
                } else {
                    exchange.sendResponseHeaders(status, out.length);
                    exchange.getResponseBody().write(out);
                }
            } finally {
                exchange.close();
            }
        }
    }

    // ---- HTTP helpers and assertions ---------------------------------------------------------

    private record Resp(int status, HttpHeaders headers, byte[] body) {
        String contentType() {
            return header("Content-Type");
        }

        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    private static Resp send(HttpClient http, String method, String path, String contentType, byte[] body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + frontendPort + path))
                .timeout(REQUEST_TIMEOUT);
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        b.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));
        HttpResponse<byte[]> r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new Resp(r.statusCode(), r.headers(), r.body());
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void fail(String name, String detail) {
        System.out.println("FAIL " + name + ": " + detail);
        failed++;
    }

    private static void skip(String name, String reason) {
        System.out.println("SKIPPED " + name + " (" + reason + ")");
        skipped++;
    }
}
