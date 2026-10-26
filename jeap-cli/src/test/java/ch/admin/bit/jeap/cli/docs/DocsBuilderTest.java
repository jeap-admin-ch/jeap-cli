package ch.admin.bit.jeap.cli.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocsBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsTheSampleDocsAsStaticSite() throws Exception {
        DocsSite site = DocsSite.open(DocsSiteTest.sampleDocs(), "Sample", "Preview of branch x", false);
        Path output = tempDir.resolve("_site");

        DocsBuilder.Result result = DocsBuilder.build(site, output);

        assertThat(result.pages()).isEqualTo(3);
        assertThat(result.staticFiles()).isEqualTo(1);
        assertThat(relativeFiles(output)).containsExactly(
                "1-overview/images-and-links.html",
                "1-overview/images/architecture.drawio.png",
                "2-diagrams/diagrams.html",
                "README.html",
                "_preview/preview.css",
                "_preview/preview.js",
                "_preview/vendor/mermaid.min.js",
                "_preview/vendor/plantuml.js",
                "_preview/vendor/purify.min.js",
                "_preview/vendor/themes.js",
                "_preview/vendor/viz-global.js",
                "index.html");
        assertThat(Files.readString(output.resolve("index.html")))
                .contains("Sample documentation")
                .contains("<span class=\"banner\">Preview of branch x</span>")
                .doesNotContain("reload.js");
        assertThat(Files.readString(output.resolve("2-diagrams/diagrams.html")))
                .contains("<link rel=\"stylesheet\" href=\"../_preview/preview.css\">")
                .doesNotContain("href=\"/", "src=\"/");
        assertThat(output.resolve("1-overview/images/architecture.drawio.png"))
                .hasSameBinaryContentAs(site.root().resolve("1-overview/images/architecture.drawio.png"));
    }

    @Test
    void emptiesTheOutputFolderFirst() throws IOException {
        Path docs = write("docs/page.md", "# Page\n").getParent();
        Path output = tempDir.resolve("site");
        write("site/stale.html", "old");
        write("site/old-folder/stale.png", "old");

        DocsBuilder.build(DocsSite.open(docs, "Docs", null, false), output);

        assertThat(output.resolve("stale.html")).doesNotExist();
        assertThat(output.resolve("old-folder")).doesNotExist();
        assertThat(output.resolve("page.html")).exists();
    }

    @Test
    void refusesTheSourceFolderAsOutput() throws IOException {
        Path docs = write("docs/page.md", "# Page\n").getParent();
        DocsSite site = DocsSite.open(docs, "Docs", null, false);

        assertThatThrownBy(() -> DocsBuilder.build(site, docs))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("must not be the docs folder or contain it");
        assertThat(docs.resolve("page.md")).exists();
    }

    @Test
    void refusesAnOutputFolderContainingTheSource() throws IOException {
        Path docs = write("docs/page.md", "# Page\n").getParent();
        DocsSite site = DocsSite.open(docs, "Docs", null, false);

        assertThatThrownBy(() -> DocsBuilder.build(site, tempDir))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("must not be the docs folder or contain it");
        assertThat(docs.resolve("page.md")).exists();
    }

    @Test
    void refusesAnOutputFolderInsideTheSource() throws IOException {
        Path docs = write("docs/page.md", "# Page\n").getParent();
        DocsSite site = DocsSite.open(docs, "Docs", null, false);

        assertThatThrownBy(() -> DocsBuilder.build(site, docs.resolve("site")))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("must not be inside the docs folder");
    }

    @Test
    void refusesAnOutputFolderInsideTheSourceReachedThroughASymbolicLink() throws IOException {
        // like /var -> /private/var on macOS: the docs root is a real path, the new output folder is not
        Path real = Files.createDirectories(tempDir.resolve("real"));
        write("real/docs/page.md", "# Page\n");
        Path link = Files.createSymbolicLink(tempDir.resolve("link"), real);
        DocsSite site = DocsSite.open(link.resolve("docs"), "Docs", null, false);

        assertThatThrownBy(() -> DocsBuilder.build(site, link.resolve("docs/new/site")))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("must not be inside the docs folder");
    }

    @Test
    void refusesAFileAsOutput() throws IOException {
        Path docs = write("docs/page.md", "# Page\n").getParent();
        Path file = write("output.txt", "x");
        DocsSite site = DocsSite.open(docs, "Docs", null, false);

        assertThatThrownBy(() -> DocsBuilder.build(site, file))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("is not a folder");
    }

    private List<String> relativeFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> folder.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private Path write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
