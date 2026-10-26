package ch.admin.bit.jeap.cli.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Markdown pages and static files of a docs folder, rendered as HTML pages with a navigation that mirrors the
 * folder tree. Pages are rendered from the current files on every call, so the preview always shows the latest state.
 * <p>
 * Files and folders whose name starts with {@code .} or {@code _} are ignored, like on the doc service. A folder's
 * {@code README.md} or {@code index.md} is the folder's own page, the one in the root folder is the start page.
 * </p>
 */
public final class DocsSite {

    private static final List<String> FOLDER_PAGE_NAMES = List.of("readme.md", "index.md");
    private static final String START_PAGE = "index.html";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final Path root;
    private final String title;
    private final String banner;
    private final boolean liveReload;
    private final MarkdownRenderer markdown = new MarkdownRenderer();
    private final String template = DocsAssets.pageTemplate();

    private DocsSite(Path root, String title, String banner, boolean liveReload) {
        this.root = root;
        this.title = title;
        this.banner = banner;
        this.liveReload = liveReload;
    }

    /**
     * Opens a docs folder.
     *
     * @param source     the docs folder
     * @param title      the site title shown in the header
     * @param banner     text shown at the top of every page, or {@code null}
     * @param liveReload whether pages reload when a file changes (local preview only)
     * @throws DocsException if the folder does not exist or contains no Markdown pages
     */
    public static DocsSite open(Path source, String title, String banner, boolean liveReload) {
        Path absolute = source.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw new DocsException("Docs folder not found: " + absolute + ". Use --source to set the docs folder.");
        }
        Path root;
        try {
            root = absolute.toRealPath();
        } catch (IOException e) {
            throw new DocsException("Cannot read the docs folder " + absolute + ": " + e.getMessage(), e);
        }
        DocsSite site = new DocsSite(root, title, banner, liveReload);
        if (site.pages().isEmpty()) {
            throw new DocsException("The docs folder " + root + " contains no Markdown pages (*.md).");
        }
        return site;
    }

    public Path root() {
        return root;
    }

    /**
     * Returns the visible files of the docs folder (pages and static files), sorted by path.
     */
    List<Path> files() {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    return dir.equals(root) || isVisible(dir) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && isVisible(file)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    // the file vanished or is not readable while walking; skip it
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        files.sort(Comparator.naturalOrder());
        return files;
    }

    /**
     * Returns the Markdown pages, sorted by path.
     */
    List<Path> pages() {
        return files().stream().filter(DocsSite::isPage).toList();
    }

    /**
     * Returns the static files (everything but pages), sorted by path.
     */
    List<Path> staticFiles() {
        return files().stream().filter(file -> !isPage(file)).toList();
    }

    /**
     * Returns a fingerprint of the visible files (path, size and modification time) that changes whenever a file is
     * added, removed or modified.
     */
    String fingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        for (Path file : files()) {
            try {
                fingerprint.append(root.relativize(file)).append(':')
                        .append(Files.size(file)).append(':')
                        .append(Files.getLastModifiedTime(file).toMillis()).append('\n');
            } catch (IOException e) {
                // the file vanished since the walk; the next poll sees the change
            }
        }
        return fingerprint.toString();
    }

    /**
     * Returns the path of the rendered page relative to the site root, e.g. {@code a/b.html} for {@code a/b.md}.
     */
    String htmlPath(Path page) {
        String relative = relativePath(page);
        return relative.substring(0, relative.length() - ".md".length()) + ".html";
    }

    String relativePath(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /**
     * Returns the page for a request path of a rendered page ({@code a/b.html}), if there is one.
     */
    Optional<Path> pageForHtmlPath(String htmlPath) {
        if (!htmlPath.endsWith(".html")) {
            return Optional.empty();
        }
        String pagePath = htmlPath.substring(0, htmlPath.length() - ".html".length()) + ".md";
        return pages().stream().filter(page -> relativePath(page).equals(pagePath)).findFirst();
    }

    /**
     * Renders the start page: the root {@code README.md} or {@code index.md}, else an overview of all pages.
     */
    String renderStartPage() {
        NavigationEntry tree = navigationTree();
        Optional<Path> startPage = startPage();
        if (startPage.isPresent()) {
            return renderPage(startPage.get(), tree);
        }
        StringBuilder content = new StringBuilder("<h1>").append(Html.escape(title)).append("</h1>\n");
        overview(tree, content);
        return layout(title, content.toString(), "", tree, null);
    }

    String renderPage(Path page) {
        return renderPage(page, navigationTree());
    }

    private String renderPage(Path page, NavigationEntry tree) {
        MarkdownRenderer.RenderedPage rendered = markdown.render(read(page), page.getFileName().toString());
        String prefix = "../".repeat(depth(htmlPath(page)));
        return layout(rendered.title(), rendered.html(), prefix, tree, page);
    }

    Optional<Path> startPage() {
        for (String name : FOLDER_PAGE_NAMES) {
            Optional<Path> page = pages().stream()
                    .filter(candidate -> candidate.getParent().equals(root) && isNamed(candidate, name))
                    .findFirst();
            if (page.isPresent()) {
                return page;
            }
        }
        return Optional.empty();
    }

    // ---- navigation ----

    record NavigationEntry(String name, String label, Path path, boolean folder, Path folderPage,
                           List<NavigationEntry> children) {

        boolean hasPages() {
            return folderPage != null || children.stream().anyMatch(child -> !child.folder() || child.hasPages());
        }
    }

    NavigationEntry navigationTree() {
        Map<Path, List<Path>> pagesByFolder = new HashMap<>();
        for (Path page : pages()) {
            for (Path folder = page.getParent(); folder.startsWith(root); folder = folder.getParent()) {
                pagesByFolder.computeIfAbsent(folder, ignored -> new ArrayList<>());
                if (folder.equals(root)) {
                    break;
                }
            }
            pagesByFolder.get(page.getParent()).add(page);
        }
        return folderEntry(root, pagesByFolder);
    }

    private NavigationEntry folderEntry(Path folder, Map<Path, List<Path>> pagesByFolder) {
        List<NavigationEntry> children = new ArrayList<>();
        Path folderPage = null;
        List<Path> folderPages = pagesByFolder.getOrDefault(folder, List.of());
        for (String name : FOLDER_PAGE_NAMES) {
            Optional<Path> candidate = folderPages.stream().filter(page -> isNamed(page, name)).findFirst();
            if (candidate.isPresent()) {
                folderPage = candidate.get();
                break;
            }
        }
        for (Path page : folderPages) {
            if (!page.equals(folderPage)) {
                String fileName = page.getFileName().toString();
                children.add(new NavigationEntry(fileName, markdown.title(read(page), fileName), page, false, null,
                        List.of()));
            }
        }
        for (Path subfolder : pagesByFolder.keySet()) {
            if (folder.equals(subfolder.getParent())) {
                NavigationEntry entry = folderEntry(subfolder, pagesByFolder);
                if (entry.hasPages()) {
                    children.add(entry);
                }
            }
        }
        children.sort(Comparator.comparingInt((NavigationEntry entry) -> MarkdownRenderer.sortNumber(entry.name()))
                .thenComparing(entry -> entry.label().toLowerCase(Locale.ROOT)));
        String name = folder.equals(root) ? "" : folder.getFileName().toString();
        // the start page is not listed in the navigation, it is linked from the site title
        Path ownPage = folder.equals(root) ? null : folderPage;
        return new NavigationEntry(name, MarkdownRenderer.folderLabel(name), folder, true, ownPage, children);
    }

    private void navigation(NavigationEntry folder, StringBuilder html, String prefix, Path current) {
        html.append("<ul>");
        for (NavigationEntry entry : folder.children()) {
            if (entry.folder()) {
                boolean open = current != null && current.startsWith(entry.path());
                html.append("<li class=\"folder\"><details").append(open ? " open" : "").append("><summary>");
                if (entry.folderPage() != null) {
                    link(html, prefix, entry.folderPage(), entry.label(), entry.folderPage().equals(current));
                } else {
                    html.append(Html.escape(entry.label()));
                }
                html.append("</summary>");
                navigation(entry, html, prefix, current);
                html.append("</details></li>");
            } else {
                html.append("<li>");
                link(html, prefix, entry.path(), entry.label(), entry.path().equals(current));
                html.append("</li>");
            }
        }
        html.append("</ul>");
    }

    private void overview(NavigationEntry folder, StringBuilder html) {
        html.append("<ul class=\"overview\">");
        for (NavigationEntry entry : folder.children()) {
            html.append("<li>");
            if (entry.folder()) {
                if (entry.folderPage() != null) {
                    link(html, "", entry.folderPage(), entry.label(), false);
                } else {
                    html.append("<strong>").append(Html.escape(entry.label())).append("</strong>");
                }
                overview(entry, html);
            } else {
                link(html, "", entry.path(), entry.label(), false);
            }
            html.append("</li>");
        }
        html.append("</ul>");
    }

    private void link(StringBuilder html, String prefix, Path page, String label, boolean active) {
        html.append("<a");
        if (active) {
            html.append(" class=\"active\" aria-current=\"page\"");
        }
        html.append(" href=\"").append(Html.escape(prefix + htmlPath(page))).append("\">")
                .append(Html.escape(label)).append("</a>");
    }

    // ---- layout ----

    private String layout(String pageTitle, String content, String prefix, NavigationEntry tree, Path current) {
        StringBuilder navigation = new StringBuilder();
        navigation(tree, navigation, prefix, current);
        String assets = prefix + DocsAssets.ASSETS_FOLDER;
        Map<String, String> values = Map.of(
                "pageTitle", Html.escape(pageTitle),
                "siteTitle", Html.escape(title),
                "banner", banner == null || banner.isBlank() ? ""
                        : "<span class=\"banner\">" + Html.escape(banner) + "</span>",
                "assets", Html.escape(assets),
                "home", Html.escape(prefix + START_PAGE),
                "reloadScript", liveReload
                        ? "<script src=\"" + Html.escape(assets + DocsAssets.RELOAD_SCRIPT) + "\"></script>" : "",
                "navigation", navigation.toString(),
                "content", content);
        // replace all placeholders in one pass, so placeholders in the page content stay as they are
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder page = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(page, Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "")));
        }
        matcher.appendTail(page);
        return page.toString();
    }

    // ---- helpers ----

    private static boolean isVisible(Path path) {
        String name = path.getFileName().toString();
        return !name.startsWith(".") && !name.startsWith("_");
    }

    private static boolean isPage(Path file) {
        return file.getFileName().toString().endsWith(".md");
    }

    private static boolean isNamed(Path file, String lowerCaseName) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).equals(lowerCaseName);
    }

    private static int depth(String relativePath) {
        return (int) relativePath.chars().filter(c -> c == '/').count();
    }

    private static String read(Path page) {
        try {
            // decode leniently: a page with a stray non-UTF-8 byte still renders
            return new String(Files.readAllBytes(page), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
