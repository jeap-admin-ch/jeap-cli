package ch.admin.bit.jeap.cli.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Writes a docs site as static HTML: every page {@code a/b.md} as {@code a/b.html}, the start page as
 * {@code index.html}, the static files as they are and the preview assets below {@code _preview/}. All links are
 * relative, so the site works under any base path.
 */
public final class DocsBuilder {

    public record Result(Path output, int pages, int staticFiles) {
    }

    private DocsBuilder() {
    }

    /**
     * Builds the site into the output folder. The output folder is emptied first.
     *
     * @throws DocsException if the output folder is the docs folder, contains it or is inside it
     */
    public static Result build(DocsSite site, Path output) throws IOException {
        Path target = validateOutput(site.root(), output);
        if (Files.exists(target)) {
            deleteContents(target);
        }
        Files.createDirectories(target);

        int staticFiles = 0;
        for (Path file : site.staticFiles()) {
            Path destination = target.resolve(site.relativePath(file));
            Files.createDirectories(destination.getParent());
            Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
            staticFiles++;
        }
        int pages = 0;
        for (Path page : site.pages()) {
            write(target.resolve(site.htmlPath(page)), site.renderPage(page).getBytes(StandardCharsets.UTF_8));
            pages++;
        }
        write(target.resolve("index.html"), site.renderStartPage().getBytes(StandardCharsets.UTF_8));
        for (String asset : DocsAssets.PAGE_ASSETS) {
            write(target.resolve(DocsAssets.ASSETS_FOLDER + asset), DocsAssets.require(asset));
        }
        return new Result(target, pages, staticFiles);
    }

    private static Path validateOutput(Path source, Path output) throws IOException {
        Path target = output.toAbsolutePath().normalize();
        Path resolvedTarget = realPath(target);
        if (source.startsWith(resolvedTarget)) {
            throw new DocsException("The output folder " + target + " must not be the docs folder or contain it.");
        }
        if (resolvedTarget.startsWith(source)) {
            throw new DocsException("The output folder " + target + " must not be inside the docs folder " + source + ".");
        }
        if (Files.exists(target) && !Files.isDirectory(target)) {
            throw new DocsException("The output path " + target + " exists and is not a folder.");
        }
        return target;
    }

    /**
     * Returns the real path of a path that may not exist yet: the real path of its nearest existing ancestor with the
     * rest appended. The docs root is a real path, so symbolic links (e.g. /var to /private/var on macOS) must be
     * resolved before comparing.
     */
    private static Path realPath(Path path) throws IOException {
        Path existing = path;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return path;
        }
        return existing.toRealPath().resolve(existing.relativize(path)).normalize();
    }

    private static void deleteContents(Path folder) throws IOException {
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                if (!dir.equals(folder)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
