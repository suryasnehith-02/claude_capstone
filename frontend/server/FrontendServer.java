/*
 * CAP-1 frontend server: serves ./public and proxies /todos and /todos/* to the CAP-2 backend.
 *
 * Run from the frontend/ directory (JDK 21+, no build step, no dependency):
 *   java server/FrontendServer.java [--port=8000] [--backend-port=8080]
 * From the repository root: java frontend/server/FrontendServer.java [...]
 * The web root is ./public, or ./frontend/public when ./public has no index.html.
 *
 * Binds to 127.0.0.1 only. Writes no files. Stop with Ctrl+C.
 */

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public final class FrontendServer {

    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8000;
    private static final int DEFAULT_BACKEND_PORT = 8080;
    private static final String USAGE =
            "usage: java server/FrontendServer.java [--port=<n>] [--backend-port=<n>]";

    private static final Map<String, String> MIME = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            // Browsers refuse module scripts that are not served with a JavaScript MIME type.
            ".js", "text/javascript; charset=utf-8",
            ".json", "application/json; charset=utf-8",
            ".svg", "image/svg+xml",
            ".ico", "image/x-icon",
            ".png", "image/png");
    private static final String TEXT_PLAIN = "text/plain; charset=utf-8";
    private static final String JSON = "application/json";

    private final Path root;
    private final String backendBase;
    private final HttpClient client;

    private FrontendServer(Path root, int backendPort) {
        this.root = root;
        this.backendBase = "http://" + HOST + ":" + backendPort;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        int backendPort = DEFAULT_BACKEND_PORT;
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = parsePort(arg.substring("--port=".length()));
            } else if (arg.startsWith("--backend-port=")) {
                backendPort = parsePort(arg.substring("--backend-port=".length()));
            } else {
                usageError("unknown argument: " + arg);
            }
        }

        Path root = findWebRoot();
        if (root == null) {
            System.err.println("index.html not found in ./public or ./frontend/public: run from the frontend/ directory"
                    + " or from the repository root");
            System.exit(1);
            return;
        }

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(HOST), port), 0);
        } catch (BindException e) {
            System.err.println("port " + port + " is already in use; choose another with --port=<n>");
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("cannot start the server: " + e.getClass().getSimpleName());
            System.exit(1);
            return;
        }

        FrontendServer app = new FrontendServer(root, backendPort);
        server.createContext("/", app::handle);
        // A slow backend call must never hold up static files.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Frontend at http://" + HOST + ":" + port + "/ - proxying /todos to "
                + app.backendBase);
        System.out.flush();
    }

    /** The web root is ./public (started from frontend/) or ./frontend/public (started from the repository root). */
    private static Path findWebRoot() {
        for (String candidate : new String[] {"public", "frontend/public"}) {
            Path dir = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(dir.resolve("index.html"))) {
                return dir;
            }
        }
        return null;
    }

    private static int parsePort(String text) {
        try {
            int value = Integer.parseInt(text);
            if (value >= 1 && value <= 65535) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the usage error
        }
        usageError("not a valid port: " + text);
        return 0;
    }

    private static void usageError(String message) {
        System.err.println(message);
        System.err.println(USAGE);
        System.exit(2);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String rawPath = exchange.getRequestURI().getRawPath();
            if (rawPath == null) {
                rawPath = "";
            }
            if (rawPath.equals("/todos") || rawPath.startsWith("/todos/")) {
                proxy(exchange, rawPath);
            } else {
                serveStatic(exchange);
            }
        } finally {
            exchange.close();
        }
    }

    // ---- proxy -------------------------------------------------------------------------------

    private void proxy(HttpExchange exchange, String rawPath) {
        String method = exchange.getRequestMethod();
        try {
            String rawQuery = exchange.getRequestURI().getRawQuery();
            URI target = URI.create(backendBase + rawPath + (rawQuery != null ? "?" + rawQuery : ""));
            byte[] requestBody = exchange.getRequestBody().readAllBytes();

            HttpRequest.Builder builder = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(30));
            copyHeader(exchange, builder, "Content-Type");
            copyHeader(exchange, builder, "Accept");
            builder.method(method, requestBody.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(requestBody));

            HttpResponse<byte[]> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            send(exchange, response.statusCode(), contentType, response.body());
        } catch (HttpConnectTimeoutException e) {
            fail(exchange, rawPath, e, 502, "BAD_GATEWAY", "Backend unavailable");
        } catch (HttpTimeoutException e) {
            fail(exchange, rawPath, e, 504, "GATEWAY_TIMEOUT", "Backend did not answer in time");
        } catch (IOException e) {
            fail(exchange, rawPath, e, 502, "BAD_GATEWAY", "Backend unavailable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(exchange, rawPath, e, 502, "BAD_GATEWAY", "Backend unavailable");
        } catch (IllegalArgumentException e) {
            fail(exchange, rawPath, e, 502, "BAD_GATEWAY", "Backend unavailable");
        } catch (RuntimeException e) {
            fail(exchange, rawPath, e, 502, "BAD_GATEWAY", "Backend unavailable");
        }
    }

    private static void copyHeader(HttpExchange exchange, HttpRequest.Builder builder, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        if (value != null) {
            builder.header(name, value);
        }
    }

    /** Logs one line (no ids, bodies or titles) and answers with the JSON envelope if nothing was sent yet. */
    private static void fail(HttpExchange exchange, String rawPath, Exception e, int status,
            String code, String message) {
        System.err.println("proxy: " + safeMethod(exchange.getRequestMethod()) + " " + routeKind(rawPath)
                + " failed: " + e.getClass().getSimpleName());
        if (exchange.getResponseCode() != -1) {
            return;
        }
        String body = "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}";
        try {
            send(exchange, status, JSON, body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // the browser is gone; nothing more can be done
        }
    }

    private static String routeKind(String rawPath) {
        if (rawPath.equals("/todos")) {
            return "collection";
        }
        return rawPath.endsWith("/status") ? "status" : "item";
    }

    private static String safeMethod(String method) {
        return method != null && method.matches("[A-Z]{1,16}") ? method : "?";
    }

    // ---- static files ------------------------------------------------------------------------

    private void serveStatic(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null) {
                sendText(exchange, 404, "Not Found");
                return;
            }
            // Resolving "/js/main.js" against the root would give a drive-root path on Windows.
            String relative = path.startsWith("/") ? path.substring(1) : path;

            Path file;
            try {
                file = root.resolve(relative).normalize();
            } catch (InvalidPathException e) {
                sendText(exchange, 404, "Not Found");
                return;
            }
            if (!file.startsWith(root)) {
                sendText(exchange, 404, "Not Found");
                return;
            }

            if (Files.isDirectory(file)) {
                if (!path.endsWith("/")) {
                    exchange.getResponseHeaders().set("Location", exchange.getRequestURI().getRawPath() + "/");
                    send(exchange, 301, null, new byte[0]);
                    return;
                }
                file = file.resolve("index.html");
            }
            if (!Files.isRegularFile(file)) {
                sendText(exchange, 404, "Not Found");
                return;
            }

            send(exchange, 200, mimeFor(file), Files.readAllBytes(file));
        } catch (IOException | RuntimeException e) {
            System.err.println("static: failed: " + e.getClass().getSimpleName());
            if (exchange.getResponseCode() == -1) {
                sendText(exchange, 500, "Internal Server Error");
            }
        }
    }

    private static String mimeFor(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "application/octet-stream" : MIME.getOrDefault(name.substring(dot), "application/octet-stream");
    }

    // ---- responses ---------------------------------------------------------------------------

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        send(exchange, status, TEXT_PLAIN, text.getBytes(StandardCharsets.UTF_8));
    }

    /** 204 or an empty body is sent with length -1; otherwise the exact length (0 would mean chunked). */
    private static void send(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        Headers headers = exchange.getResponseHeaders();
        if (contentType != null) {
            headers.set("Content-Type", contentType);
        }
        headers.set("Cache-Control", "no-store");
        if (status == 204 || body.length == 0 || exchange.getRequestMethod().equals("HEAD")) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
