package ch.admin.bit.jeap.cli.docs;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * The preview's own assets and the bundled JavaScript libraries, read from the class path ({@code docs-preview/}).
 * Pages reference them below {@value #ASSETS_FOLDER}, which cannot clash with docs files as those may not start with
 * {@code _}.
 */
final class DocsAssets {

    static final String ASSETS_FOLDER = "_preview/";
    static final String RELOAD_SCRIPT = "reload.js";

    /** Assets of every page; {@code docs build} copies them into the static site. */
    static final List<String> PAGE_ASSETS = List.of(
            "preview.css",
            "preview.js",
            "vendor/mermaid.min.js",
            "vendor/plantuml.js",
            "vendor/viz-global.js",
            "vendor/themes.js",
            "vendor/purify.min.js");

    private static final String CLASSPATH_FOLDER = "docs-preview/";
    private static final String PAGE_TEMPLATE = "page.html";

    private DocsAssets() {
    }

    /**
     * Returns an asset by its name below {@value #ASSETS_FOLDER}; only the known assets are served.
     */
    static Optional<byte[]> read(String name) {
        if (!PAGE_ASSETS.contains(name) && !RELOAD_SCRIPT.equals(name)) {
            return Optional.empty();
        }
        return readClasspath(name);
    }

    static byte[] require(String name) {
        return read(name).orElseThrow(() -> new DocsException(
                "The bundled docs preview asset " + name + " is missing from the jEAP CLI."));
    }

    static String pageTemplate() {
        return readClasspath(PAGE_TEMPLATE)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .orElseThrow(() -> new DocsException("The docs preview page template is missing from the jEAP CLI."));
    }

    private static Optional<byte[]> readClasspath(String name) {
        try (InputStream in = DocsAssets.class.getClassLoader().getResourceAsStream(CLASSPATH_FOLDER + name)) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
