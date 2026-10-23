package ch.admin.bit.jeap.cli.docs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Serves a docs site on the loopback interface for the local preview. Pages are rendered on every request from the
 * current files. Open pages reload over server-sent events when a file in the docs folder changes.
 */
@Slf4j
public final class DocsPreviewServer implements AutoCloseable {

    static final long POLL_INTERVAL_MILLIS = 500;
    private static final long PING_INTERVAL_SECONDS = 15;
    private static final String EVENTS_PATH = "/" + DocsAssets.ASSETS_FOLDER + "events";
    private static final String CHANGE = "change";
    private static final String CLOSE = "close";
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("avif", "image/avif"),
            Map.entry("json", "application/json"),
            Map.entry("txt", "text/plain; charset=utf-8"));

    private final DocsSite site;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<BlockingQueue<String>> clients = new CopyOnWriteArrayList<>();
    private final Thread poller;
    private volatile boolean closed;

    private DocsPreviewServer(DocsSite site, HttpServer server) {
        this.site = site;
        this.server = server;
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        this.poller = Thread.ofPlatform().daemon().name("docs-preview-watcher").unstarted(this::pollForChanges);
    }

    /**
     * Starts the server on 127.0.0.1. If the port is taken, a free port is used instead; see {@link #port()}.
     */
    public static DocsPreviewServer start(DocsSite site, int port) throws IOException {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        } catch (BindException e) {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        }
        DocsPreviewServer previewServer = new DocsPreviewServer(site, server);
        server.start();
        previewServer.poller.start();
        return previewServer;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public URI uri() {
        return URI.create("http://127.0.0.1:" + port() + "/");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        poller.interrupt();
        clients.forEach(queue -> queue.offer(CLOSE));
        server.stop(0);
        executor.shutdownNow();
    }

    // ---- requests ----

    private void handle(HttpExchange exchange) {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals(EVENTS_PATH)) {
                streamEvents(exchange);
            } else if (path.startsWith("/" + DocsAssets.ASSETS_FOLDER)) {
                serveAsset(exchange, path.substring(1 + DocsAssets.ASSETS_FOLDER.length()));
            } else if (path.equals("/") || path.equals("/index.html")) {
                send(exchange, 200, "html", site.renderStartPage().getBytes(StandardCharsets.UTF_8));
            } else if (path.endsWith(".html")) {
                Optional<Path> page = site.pageForHtmlPath(path.substring(1));
                if (page.isPresent()) {
                    send(exchange, 200, "html", site.renderPage(page.get()).getBytes(StandardCharsets.UTF_8));
                } else {
                    notFound(exchange);
                }
            } else {
                serveStaticFile(exchange, path.substring(1));
            }
        } catch (IOException e) {
            // the browser closed the connection
        } catch (RuntimeException e) {
            log.warn("Docs preview request {} failed: {}", exchange.getRequestURI().getPath(), e.getMessage());
            sendQuietly(exchange, 500, ("Rendering failed: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void serveAsset(HttpExchange exchange, String name) throws IOException {
        Optional<byte[]> asset = DocsAssets.read(name);
        if (asset.isPresent()) {
            send(exchange, 200, extension(name), asset.get());
        } else {
            notFound(exchange);
        }
    }

    private void serveStaticFile(HttpExchange exchange, String relativePath) throws IOException {
        Path file = resolveInside(site.root(), relativePath);
        if (file == null || !Files.isRegularFile(file) || !isVisible(site.root(), file)) {
            notFound(exchange);
            return;
        }
        send(exchange, 200, extension(file.getFileName().toString()), Files.readAllBytes(file));
    }

    /**
     * Resolves a decoded request path inside the root folder. Returns {@code null} if the path leaves the root,
     * also through a symbolic link.
     */
    static Path resolveInside(Path root, String relativePath) {
        try {
            Path realRoot = root.toRealPath();
            Path candidate = realRoot.resolve(relativePath).normalize();
            if (!candidate.startsWith(realRoot)) {
                return null;
            }
            if (Files.exists(candidate) && !candidate.toRealPath().startsWith(realRoot)) {
                return null;
            }
            return candidate;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isVisible(Path root, Path file) {
        for (Path segment : root.relativize(file)) {
            String name = segment.toString();
            if (name.startsWith(".") || name.startsWith("_")) {
                return false;
            }
        }
        return true;
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        send(exchange, 404, "txt", "Not found".getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String extension, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType(extension));
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void sendQuietly(HttpExchange exchange, int status, byte[] body) {
        try {
            send(exchange, status, "txt", body);
        } catch (IOException | RuntimeException e) {
            // response already started or connection closed
        }
    }

    static String contentType(String extension) {
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // ---- auto reload ----

    private void streamEvents(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, 0);
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        clients.add(queue);
        try (OutputStream out = exchange.getResponseBody()) {
            write(out, ": connected\n\n");
            while (!closed) {
                String event = queue.poll(PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
                if (CLOSE.equals(event)) {
                    return;
                }
                // the ping detects closed tabs: writing to a closed connection fails and ends this handler
                write(out, event == null ? ": ping\n\n" : "data: " + event + "\n\n");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clients.remove(queue);
        }
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * Polls the docs folder instead of using a {@code WatchService}: on macOS the JDK's watch service polls with a
     * delay of several seconds and needs a registration per folder, and docs trees are small.
     */
    private void pollForChanges() {
        String last = fingerprintOrEmpty();
        while (!closed) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            String current = fingerprintOrEmpty();
            if (!current.equals(last)) {
                last = current;
                clients.forEach(queue -> queue.offer(CHANGE));
            }
        }
    }

    private String fingerprintOrEmpty() {
        try {
            return site.fingerprint();
        } catch (RuntimeException e) {
            // the docs folder is temporarily unavailable, e.g. while switching branches
            return "";
        }
    }

    int connectedClients() {
        return clients.size();
    }
}
