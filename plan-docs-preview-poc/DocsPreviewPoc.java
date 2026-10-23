import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Throwaway proof of concept for the docs preview: renders a docs folder on each request, serves it on
 * 127.0.0.1 and reloads open pages over server-sent events when a file changes.
 * Usage: java -cp <commonmark jars> DocsPreviewPoc.java <source> <port> <title> <banner> <vendor dir> <assets dir>
 */
public class DocsPreviewPoc {

    static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create(),
            AutolinkExtension.create(), YamlFrontMatterExtension.create(), HeadingAnchorExtension.create());
    static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    static final Set<String> MERMAID = Set.of("mermaid");
    static final Set<String> PLANTUML = Set.of("plantuml", "puml");
    static final Set<String> GRAPHVIZ = Set.of("dot", "graphviz", "gv");
    static final Pattern NUMBER_PREFIX = Pattern.compile("^(\\d+)[-_ ]+(.*)$");
    static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"), Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"), Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("avif", "image/avif"),
            Map.entry("json", "application/json"), Map.entry("txt", "text/plain; charset=utf-8"));

    static Path source;
    static String siteTitle;
    static String banner;
    static Path vendorDir;
    static Path assetsDir;
    static final List<BlockingQueue<String>> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        source = Path.of(args[0]).toAbsolutePath().normalize().toRealPath();
        int port = Integer.parseInt(args[1]);
        siteTitle = args[2];
        banner = args[3];
        vendorDir = Path.of(args[4]).toAbsolutePath();
        assetsDir = Path.of(args[5]).toAbsolutePath();

        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", DocsPreviewPoc::handle);
        server.start();
        Thread.ofPlatform().daemon().start(DocsPreviewPoc::pollForChanges);
        System.out.println("Docs preview: http://127.0.0.1:" + port + "/ (Ctrl+C to stop)");
    }

    // ---- HTTP ----

    static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/_preview/events")) {
                streamEvents(exchange);
            } else if (path.startsWith("/_preview/vendor/")) {
                serveFile(exchange, vendorDir, path.substring("/_preview/vendor/".length()));
            } else if (path.startsWith("/_preview/")) {
                serveFile(exchange, assetsDir, path.substring("/_preview/".length()));
            } else if (path.equals("/") || path.equals("/index.html")) {
                send(exchange, 200, "html", startPage().getBytes(StandardCharsets.UTF_8));
            } else if (path.endsWith(".html")) {
                String rel = path.substring(1, path.length() - ".html".length()) + ".md";
                Path md = resolveInside(source, rel);
                if (md == null || !Files.isRegularFile(md)) {
                    send(exchange, 404, "txt", "Not found".getBytes(StandardCharsets.UTF_8));
                } else {
                    send(exchange, 200, "html", renderPage(md).getBytes(StandardCharsets.UTF_8));
                }
            } else {
                serveFile(exchange, source, path.substring(1));
            }
        } catch (IOException e) {
            // client went away
        }
    }

    static void serveFile(HttpExchange exchange, Path root, String rel) throws IOException {
        Path file = resolveInside(root, rel);
        if (file == null || !Files.isRegularFile(file)) {
            send(exchange, 404, "txt", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(exchange, 200, extension(file.getFileName().toString()), Files.readAllBytes(file));
    }

    /** Resolves a request path inside root; null if it would leave root (including via symlinks). */
    static Path resolveInside(Path root, String rel) {
        try {
            Path realRoot = root.toRealPath();
            Path candidate = realRoot.resolve(rel).normalize();
            if (!candidate.startsWith(realRoot)) {
                return null;
            }
            if (Files.exists(candidate) && !candidate.toRealPath().startsWith(realRoot)) {
                return null;
            }
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }

    static void send(HttpExchange exchange, int status, String ext, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPES.getOrDefault(ext, "application/octet-stream"));
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    static void streamEvents(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, 0);
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        clients.add(queue);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            while (true) {
                String event = queue.poll(15, TimeUnit.SECONDS);
                out.write((event == null ? ": ping\n\n" : "data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clients.remove(queue);
        }
    }

    static void pollForChanges() {
        String last = fingerprint();
        while (true) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }
            String current = fingerprint();
            if (!current.equals(last)) {
                last = current;
                System.out.println("Change detected, reloading " + clients.size() + " page(s)");
                clients.forEach(q -> q.offer("change"));
            }
        }
    }

    static String fingerprint() {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(Files::isRegularFile).sorted().forEach(f -> {
                try {
                    sb.append(source.relativize(f)).append(':').append(Files.size(f)).append(':')
                            .append(Files.getLastModifiedTime(f).toMillis()).append('\n');
                } catch (IOException ignored) {
                    // file vanished while walking
                }
            });
        } catch (IOException ignored) {
            // source temporarily unavailable
        }
        return sb.toString();
    }

    // ---- Site model ----

    record Entry(String name, String label, Path path, boolean folder, Path folderPage, List<Entry> children) {
    }

    static Entry tree(Path dir) throws IOException {
        List<Entry> children = new ArrayList<>();
        Path folderPage = null;
        try (Stream<Path> list = Files.list(dir)) {
            for (Path p : list.toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith(".") || name.startsWith("_")) {
                    continue;
                }
                if (Files.isDirectory(p)) {
                    Entry child = tree(p);
                    if (hasPages(child)) {
                        children.add(child);
                    }
                } else if (name.endsWith(".md")) {
                    if (!dir.equals(source) && (name.equalsIgnoreCase("README.md") || name.equalsIgnoreCase("index.md"))) {
                        folderPage = p;
                    } else if (!(dir.equals(source) && (name.equalsIgnoreCase("README.md") || name.equalsIgnoreCase("index.md")))) {
                        children.add(new Entry(name, pageTitle(p), p, false, null, List.of()));
                    }
                }
            }
        }
        children.sort(Comparator.comparing((Entry e) -> sortNumber(e.name())).thenComparing(e -> e.label().toLowerCase()));
        String name = dir.getFileName().toString();
        return new Entry(name, folderLabel(name), dir, true, folderPage, children);
    }

    static boolean hasPages(Entry e) {
        return e.folderPage() != null || e.children().stream().anyMatch(c -> !c.folder() || hasPages(c));
    }

    static int sortNumber(String name) {
        Matcher m = NUMBER_PREFIX.matcher(name);
        return m.matches() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    static String folderLabel(String name) {
        Matcher m = NUMBER_PREFIX.matcher(name);
        String words = (m.matches() ? m.group(2) : name).replace('-', ' ').replace('_', ' ');
        String label = words.isEmpty() ? name : Character.toUpperCase(words.charAt(0)) + words.substring(1);
        return m.matches() ? m.group(1) + ". " + label : label;
    }

    static String pageTitle(Path md) {
        try {
            Node doc = PARSER.parse(Files.readString(md));
            String fm = frontMatterTitle(doc);
            if (fm != null) {
                return fm;
            }
            String h1 = firstH1(doc);
            if (h1 != null) {
                return h1;
            }
        } catch (IOException ignored) {
            // fall back to the file name
        }
        String base = md.getFileName().toString().replaceFirst("\\.md$", "");
        Matcher m = NUMBER_PREFIX.matcher(base);
        String words = (m.matches() ? m.group(2) : base).replace('-', ' ').replace('_', ' ');
        return words.isEmpty() ? base : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String frontMatterTitle(Node doc) {
        YamlFrontMatterVisitor v = new YamlFrontMatterVisitor();
        doc.accept(v);
        List<String> t = v.getData().get("title");
        return t == null || t.isEmpty() ? null : t.getFirst().replaceAll("^['\"]|['\"]$", "");
    }

    static String firstH1(Node doc) {
        for (Node n = doc.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof Heading h && h.getLevel() == 1) {
                StringBuilder sb = new StringBuilder();
                h.accept(new AbstractVisitor() {
                    @Override
                    public void visit(Text text) {
                        sb.append(text.getLiteral());
                    }

                    @Override
                    public void visit(Code code) {
                        sb.append(code.getLiteral());
                    }
                });
                return sb.toString();
            }
        }
        return null;
    }

    // ---- Rendering ----

    static String startPage() throws IOException {
        for (String candidate : List.of("README.md", "readme.md", "index.md")) {
            Path p = source.resolve(candidate);
            if (Files.isRegularFile(p)) {
                return renderPage(p);
            }
        }
        Entry root = tree(source);
        StringBuilder body = new StringBuilder("<h1>" + esc(siteTitle) + "</h1>");
        overview(root, body, "");
        return layout(siteTitle, body.toString(), "", root, null);
    }

    static void overview(Entry folder, StringBuilder sb, String prefix) {
        sb.append("<ul class=\"overview\">");
        for (Entry e : folder.children()) {
            if (e.folder()) {
                sb.append("<li><strong>").append(esc(e.label())).append("</strong>");
                overview(e, sb, prefix);
                sb.append("</li>");
            } else {
                sb.append("<li><a href=\"").append(prefix).append(esc(htmlPath(e.path()))).append("\">")
                        .append(esc(e.label())).append("</a></li>");
            }
        }
        sb.append("</ul>");
    }

    static String renderPage(Path md) throws IOException {
        Node doc = PARSER.parse(Files.readString(md));
        doc.accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                link.setDestination(rewriteLink(link.getDestination()));
                visitChildren(link);
            }
        });
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(EXTENSIONS)
                .escapeHtml(true)
                .nodeRendererFactory(DiagramRenderer::new)
                .build();
        String html = renderer.render(doc);
        String fmTitle = frontMatterTitle(doc);
        String title = pageTitle(md);
        if (fmTitle != null && firstH1(doc) == null) {
            html = "<h1>" + esc(fmTitle) + "</h1>\n" + html;
        }
        boolean isStart = md.getParent().equals(source)
                && List.of("readme.md", "index.md").contains(md.getFileName().toString().toLowerCase());
        String rel = isStart ? "index.html" : htmlPath(md);
        int depth = rel.split("/").length - 1;
        String prefix = "../".repeat(depth);
        return layout(title, html, prefix, tree(source), md);
    }

    static String rewriteLink(String destination) {
        if (destination == null || destination.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") || destination.startsWith("/")
                || destination.startsWith("#")) {
            return destination;
        }
        int hash = destination.indexOf('#');
        String target = hash >= 0 ? destination.substring(0, hash) : destination;
        String fragment = hash >= 0 ? destination.substring(hash) : "";
        if (target.endsWith(".md")) {
            return target.substring(0, target.length() - 3) + ".html" + fragment;
        }
        return destination;
    }

    static String htmlPath(Path md) {
        String rel = source.relativize(md).toString().replace('\\', '/');
        return rel.substring(0, rel.length() - 3) + ".html";
    }

    static String layout(String title, String content, String prefix, Entry root, Path current) {
        StringBuilder nav = new StringBuilder();
        navigation(root, nav, prefix, current);
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="preview-assets" content="%5$s_preview/">
                <title>%1$s · %2$s</title>
                <link rel="stylesheet" href="%5$s_preview/preview.css">
                <script type="module" src="%5$s_preview/preview.js"></script>
                <script src="%5$s_preview/reload.js"></script>
                </head>
                <body>
                <header class="topbar">
                  <button class="nav-toggle" aria-label="Toggle navigation" onclick="document.body.classList.toggle('nav-open')">☰</button>
                  <a class="site-title" href="%5$sindex.html">%2$s</a>
                  <span class="banner">%3$s</span>
                </header>
                <div class="layout">
                  <nav class="sidebar">%4$s</nav>
                  <main class="content"><article>%6$s</article></main>
                </div>
                </body>
                </html>
                """.formatted(esc(title), esc(siteTitle), esc(banner), nav, prefix, content);
    }

    static void navigation(Entry folder, StringBuilder sb, String prefix, Path current) {
        sb.append("<ul>");
        for (Entry e : folder.children()) {
            if (e.folder()) {
                boolean open = current != null && current.startsWith(e.path());
                sb.append("<li class=\"folder\"><details").append(open ? " open" : "").append("><summary>");
                if (e.folderPage() != null) {
                    sb.append("<a").append(e.folderPage().equals(current) ? " class=\"active\"" : "")
                            .append(" href=\"").append(prefix).append(esc(htmlPath(e.folderPage()))).append("\">")
                            .append(esc(e.label())).append("</a>");
                } else {
                    sb.append(esc(e.label()));
                }
                sb.append("</summary>");
                navigation(e, sb, prefix, current);
                sb.append("</details></li>");
            } else {
                sb.append("<li><a").append(e.path().equals(current) ? " class=\"active\"" : "")
                        .append(" href=\"").append(prefix).append(esc(htmlPath(e.path()))).append("\">")
                        .append(esc(e.label())).append("</a></li>");
            }
        }
        sb.append("</ul>");
    }

    static class DiagramRenderer implements NodeRenderer {
        private final HtmlWriter html;

        DiagramRenderer(HtmlNodeRendererContext context) {
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(FencedCodeBlock.class);
        }

        @Override
        public void render(Node node) {
            FencedCodeBlock block = (FencedCodeBlock) node;
            String info = block.getInfo() == null ? "" : block.getInfo().trim().split("\\s+")[0].toLowerCase();
            String code = block.getLiteral();
            html.line();
            if (MERMAID.contains(info)) {
                html.tag("pre", Map.of("class", "mermaid"));
                html.text(code);
                html.tag("/pre");
            } else if (PLANTUML.contains(info) || GRAPHVIZ.contains(info)) {
                String kind = PLANTUML.contains(info) ? "plantuml" : "graphviz";
                html.tag("div", Map.of("class", "diagram " + kind, "data-source", code));
                html.tag("pre");
                html.text(code);
                html.tag("/pre");
                html.tag("/div");
            } else {
                html.tag("pre");
                html.tag("code", info.isEmpty() ? Map.of() : Map.of("class", "language-" + info));
                html.text(code);
                html.tag("/code");
                html.tag("/pre");
            }
            html.line();
        }
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
