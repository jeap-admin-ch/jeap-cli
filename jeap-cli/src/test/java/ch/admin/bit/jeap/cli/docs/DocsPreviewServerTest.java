package ch.admin.bit.jeap.cli.docs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DocsPreviewServerTest {

    @TempDir
    Path tempDir;

    private Path docs;
    private DocsPreviewServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void startServer() throws IOException {
        docs = tempDir.resolve("docs");
        write("docs/README.md", "# Start\n\nSee [the page](1-a/page.md).\n");
        write("docs/1-a/page.md", "# Page A\n");
        write("docs/1-a/images/pic.png", "png-bytes");
        write("docs/_drafts/draft.md", "# Draft\n");
        write("docs/.env", "SECRET=1");
        write("outside.txt", "secret");
        server = DocsPreviewServer.start(DocsSite.open(docs, "Docs", "Local preview", true), 0);
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void servesStartPageAndRenderedPages() throws Exception {
        HttpResponse<String> start = get("/");
        assertThat(start.statusCode()).isEqualTo(200);
        assertThat(start.headers().firstValue("Content-Type")).contains("text/html; charset=utf-8");
        assertThat(start.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(start.body())
                .contains(">Start</h1>")
                .contains("href=\"1-a/page.html\"")
                .contains("<script src=\"_preview/reload.js\"></script>");

        assertThat(get("/index.html").body()).contains(">Start</h1>");
        assertThat(get("/1-a/page.html").body()).contains(">Page A</h1>").contains("content=\"../_preview/\"");
    }

    @Test
    void servesStaticFilesAndAssets() throws Exception {
        HttpResponse<String> image = get("/1-a/images/pic.png");
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(image.body()).isEqualTo("png-bytes");

        HttpResponse<String> css = get("/_preview/preview.css");
        assertThat(css.statusCode()).isEqualTo(200);
        assertThat(css.headers().firstValue("Content-Type")).contains("text/css; charset=utf-8");
        assertThat(get("/_preview/reload.js").statusCode()).isEqualTo(200);
        assertThat(get("/_preview/vendor/mermaid.min.js").statusCode()).isEqualTo(200);
    }

    @Test
    void answersNotFoundForMissingAndHiddenFiles() throws Exception {
        assertThat(get("/missing.html").statusCode()).isEqualTo(404);
        assertThat(get("/1-a/missing.png").statusCode()).isEqualTo(404);
        assertThat(get("/_drafts/draft.html").statusCode()).isEqualTo(404);
        assertThat(get("/.env").statusCode()).isEqualTo(404);
        assertThat(get("/_preview/page.html").statusCode()).isEqualTo(404);
        assertThat(get("/1-a/page.md").statusCode()).isEqualTo(200);
    }

    @Test
    void pathTraversalIsNotFound() throws Exception {
        assertThat(rawStatusLine("/../outside.txt")).contains(" 404 ");
        assertThat(rawStatusLine("/../../../etc/passwd")).contains(" 404 ");
        assertThat(rawStatusLine("/%2e%2e/outside.txt")).contains(" 404 ");
        assertThat(rawStatusLine("/1-a/%2e%2e/%2e%2e/outside.txt")).contains(" 404 ");
    }

    @Test
    void resolveInsideRejectsPathsLeavingTheRoot() throws IOException {
        Path root = docs.toRealPath();

        assertThat(DocsPreviewServer.resolveInside(root, "1-a/page.md")).isEqualTo(root.resolve("1-a/page.md"));
        assertThat(DocsPreviewServer.resolveInside(root, "1-a/../README.md")).isEqualTo(root.resolve("README.md"));
        assertThat(DocsPreviewServer.resolveInside(root, "../outside.txt")).isNull();
        assertThat(DocsPreviewServer.resolveInside(root, "1-a/../../outside.txt")).isNull();
    }

    @Test
    void resolveInsideRejectsSymbolicLinksPointingOutside() throws IOException {
        Path link = docs.resolve("link.txt");
        Files.createSymbolicLink(link, tempDir.resolve("outside.txt"));

        assertThat(DocsPreviewServer.resolveInside(docs, "link.txt")).isNull();
        assertThat(rawStatusLine("/link.txt")).contains(" 404 ");
    }

    @Test
    void notifiesOpenPagesWhenAFileChanges() throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /_preview/events HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            assertThat(reader.readLine()).contains(" 200 ");
            CompletableFuture<Void> connected = CompletableFuture.runAsync(() -> readUntil(reader, ": connected"));
            connected.get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(5)).until(() -> server.connectedClients() == 1);

            write("docs/1-a/new.md", "# New\n");

            CompletableFuture<Void> change = CompletableFuture.runAsync(() -> readUntil(reader, "data: change"));
            change.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void usesAFreePortIfThePortIsTaken() throws IOException {
        try (ServerSocket taken = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             DocsPreviewServer fallback = DocsPreviewServer.start(DocsSite.open(docs, "Docs", null, true), taken.getLocalPort())) {
            assertThat(fallback.port()).isPositive().isNotEqualTo(taken.getLocalPort());
            assertThat(fallback.uri()).isEqualTo(URI.create("http://127.0.0.1:" + fallback.port() + "/"));
        }
    }

    @Test
    void contentTypes() {
        assertThat(DocsPreviewServer.contentType("svg")).isEqualTo("image/svg+xml");
        assertThat(DocsPreviewServer.contentType("jpeg")).isEqualTo("image/jpeg");
        assertThat(DocsPreviewServer.contentType("drawio")).isEqualTo("application/octet-stream");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(server.uri().resolve(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends the path as it is, without the normalization of URI or HttpClient (like curl --path-as-is).
     */
    private String rawStatusLine(String path) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
        }
    }

    private static void readUntil(BufferedReader reader, String expected) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(expected)) {
                    return;
                }
            }
            throw new IllegalStateException("Stream ended before " + expected);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
