package ch.admin.bit.jeap.cli.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocsSiteTest {

    @TempDir
    Path tempDir;

    static Path sampleDocs() throws URISyntaxException {
        return Path.of(DocsSiteTest.class.getResource("/docs-preview/sample-docs").toURI());
    }

    @Test
    void sampleDocsRenderWithNavigationLinksAndDiagrams() throws Exception {
        DocsSite site = DocsSite.open(sampleDocs(), "Sample", "Preview of branch x", false);

        String diagrams = site.renderPage(site.root().resolve("2-diagrams/diagrams.md"));
        assertThat(diagrams)
                .contains("<title>Diagrams · Sample</title>")
                .contains("<meta name=\"preview-assets\" content=\"../_preview/\">")
                .contains("<span class=\"banner\">Preview of branch x</span>")
                .contains("<a class=\"site-title\" href=\"../index.html\">Sample</a>")
                .contains("<div class=\"diagram plantuml\"")
                .contains("<div class=\"diagram graphviz\"")
                .contains("<pre class=\"mermaid\">")
                .contains("<details open><summary>2. Diagrams</summary>")
                .contains("<a class=\"active\" aria-current=\"page\" href=\"../2-diagrams/diagrams.html\">Diagrams</a>")
                .doesNotContain("reload.js");

        String links = site.renderPage(site.root().resolve("1-overview/images-and-links.md"));
        assertThat(links)
                .contains("<h1>Images and links</h1>")
                .contains("href=\"../2-diagrams/diagrams.html#plantuml\"")
                .contains("href=\"../3-context/system-context-view.html\"")
                .contains("src=\"images/architecture.drawio.png\"")
                .contains("&lt;b&gt;not bold&lt;/b&gt;")
                .contains("<del>Strikethrough</del>");
    }

    @Test
    void sampleDocsStartPageIsTheRootReadmeAndNotListed() throws Exception {
        DocsSite site = DocsSite.open(sampleDocs(), "Sample", null, false);

        String start = site.renderStartPage();
        assertThat(start)
                .contains("<h1 id=\"sample-documentation\">Sample documentation</h1>")
                .contains("href=\"2-diagrams/diagrams.html#plantuml\"")
                .contains("<meta name=\"preview-assets\" content=\"_preview/\">")
                .doesNotContain("README.html")
                .doesNotContain("class=\"banner\"");
        assertThat(site.staticFiles()).containsExactly(site.root().resolve("1-overview/images/architecture.drawio.png"));
    }

    @Test
    void arc42LayoutSortsByNumberAndUsesFolderPages() throws IOException {
        write("README.md", "# Start\n");
        write("1-introduction-and-goals/README.md", "# Introduction\n");
        write("1-introduction-and-goals/2-quality-goals.md", "# Quality goals\n");
        write("1-introduction-and-goals/1-requirements.md", "---\ntitle: Requirements overview\n---\n");
        write("10-quality-requirements/quality.md", "# Quality\n");
        write("2-constraints/constraints.md", "# Constraints\n");
        write("12-glossary/glossary.md", "# Glossary\n");

        DocsSite site = DocsSite.open(tempDir, "Docs", null, false);
        String page = site.renderPage(site.root().resolve("1-introduction-and-goals/2-quality-goals.md"));

        assertThat(page).containsSubsequence(
                "1. Introduction and goals", "Requirements overview", "Quality goals",
                "2. Constraints", "10. Quality requirements", "12. Glossary");
        assertThat(page)
                .contains("<details open><summary><a href=\"../1-introduction-and-goals/README.html\">"
                        + "1. Introduction and goals</a></summary>")
                .contains("<details><summary>2. Constraints</summary>")
                .doesNotContain(">Introduction</a></li>")
                .doesNotContain(">Start<");
    }

    @Test
    void folderPageIsHighlightedWhenShown() throws IOException {
        write("1-intro/index.md", "# Intro\n");
        write("1-intro/details.md", "# Details\n");

        DocsSite site = DocsSite.open(tempDir, "Docs", null, false);
        String page = site.renderPage(site.root().resolve("1-intro/index.md"));

        assertThat(page).contains("<summary><a class=\"active\" aria-current=\"page\" href=\"../1-intro/index.html\">");
    }

    @Test
    void flatLayoutSortsAlphabeticallyByTitle() throws IOException {
        write("zeta.md", "# Alpha page\n");
        write("alpha.md", "# Zeta page\n");
        write("Beta.md", "# beta page\n");

        DocsSite site = DocsSite.open(tempDir, "Docs", null, false);
        String page = site.renderPage(site.root().resolve("alpha.md"));

        assertThat(page)
                .containsSubsequence("Alpha page", "beta page", "Zeta page")
                .contains("<meta name=\"preview-assets\" content=\"_preview/\">")
                .contains("href=\"zeta.html\"");
    }

    @Test
    void startPageWithoutReadmeIsAGeneratedOverview() throws IOException {
        write("2-b/page.md", "# Page B\n");
        write("1-a/README.md", "# A\n");
        write("1-a/page.md", "# Page A\n");
        write("loose.md", "# Loose\n");

        DocsSite site = DocsSite.open(tempDir, "My docs", null, false);
        String start = site.renderStartPage();

        assertThat(site.startPage()).isEmpty();
        assertThat(start)
                .contains("<title>My docs · My docs</title>")
                .contains("<h1>My docs</h1>")
                .contains("<ul class=\"overview\">")
                .contains("<a href=\"1-a/README.html\">1. A</a>")
                .contains("<a href=\"1-a/page.html\">Page A</a>")
                .contains("<strong>2. B</strong>")
                .contains("<a href=\"loose.html\">Loose</a>");
    }

    @Test
    void indexMdIsTheStartPageToo() throws IOException {
        write("index.md", "# Welcome\n");
        write("other.md", "# Other\n");

        DocsSite site = DocsSite.open(tempDir, "Docs", null, false);

        assertThat(site.startPage()).contains(site.root().resolve("index.md"));
        assertThat(site.renderStartPage()).contains(">Welcome</h1>");
    }

    @Test
    void ignoresFilesAndFoldersStartingWithDotOrUnderscore() throws IOException {
        write("page.md", "# Page\n");
        write("_drafts/draft.md", "# Draft\n");
        write(".hidden/secret.md", "# Secret\n");
        write("_partial.md", "# Partial\n");
        write(".notes.md", "# Notes\n");
        write("images/_raw.png", "x");
        write("images/ok.png", "x");

        DocsSite site = DocsSite.open(tempDir, "Docs", null, false);

        assertThat(site.pages()).containsExactly(site.root().resolve("page.md"));
        assertThat(site.staticFiles()).containsExactly(site.root().resolve("images/ok.png"));
        assertThat(site.renderStartPage()).doesNotContain("Draft", "Secret", "Partial", "Notes");
    }

    @Test
    void foldersWithoutPagesAreNotListed() throws IOException {
        write("page.md", "# Page\n");
        write("images/diagram.png", "x");
        write("empty/nested/file.txt", "x");

        DocsSite site = DocsSite.open(tempDir, "Docs", null, false);

        assertThat(site.renderPage(site.root().resolve("page.md"))).doesNotContain("Images", "Empty", "Nested");
    }

    @Test
    void pageForHtmlPath() throws IOException {
        write("a/b.md", "# B\n");

        DocsSite site = DocsSite.open(tempDir, "Docs", null, false);

        assertThat(site.pageForHtmlPath("a/b.html")).contains(site.root().resolve("a/b.md"));
        assertThat(site.pageForHtmlPath("a/c.html")).isEmpty();
        assertThat(site.pageForHtmlPath("a/b.md")).isEmpty();
    }

    @Test
    void liveReloadAddsTheReloadScript() throws IOException {
        write("a/page.md", "# Page\n");

        DocsSite site = DocsSite.open(tempDir, "Docs", "Local preview", true);

        assertThat(site.renderPage(site.root().resolve("a/page.md")))
                .contains("<script src=\"../_preview/reload.js\"></script>");
    }

    @Test
    void placeholdersInPageContentAreNotReplaced() throws IOException {
        write("page.md", "# Page\n\nTemplate syntax: {{content}} and {{siteTitle}}\n");

        DocsSite site = DocsSite.open(tempDir, "Docs", null, false);

        assertThat(site.renderPage(site.root().resolve("page.md"))).contains("Template syntax: {{content}} and {{siteTitle}}");
    }

    @Test
    void escapesSiteTitleAndBanner() throws IOException {
        write("page.md", "# Page\n");

        DocsSite site = DocsSite.open(tempDir, "<Docs>", "Branch \"<x>\"", false);

        assertThat(site.renderStartPage())
                .contains("<a class=\"site-title\" href=\"index.html\">&lt;Docs&gt;</a>")
                .contains("<span class=\"banner\">Branch &quot;&lt;x&gt;&quot;</span>");
    }

    @Test
    void fingerprintChangesWhenFilesChange() throws IOException {
        Path page = write("page.md", "# Page\n");
        DocsSite site = DocsSite.open(tempDir, "Docs", null, false);
        String initial = site.fingerprint();

        assertThat(site.fingerprint()).isEqualTo(initial);

        Files.writeString(page, "# Page, edited\n");
        Files.setLastModifiedTime(page, FileTime.from(Instant.now().plusSeconds(5)));
        String edited = site.fingerprint();
        assertThat(edited).isNotEqualTo(initial);

        write("new.md", "# New\n");
        assertThat(site.fingerprint()).isNotEqualTo(edited);

        String withNewPage = site.fingerprint();
        write("_ignored.md", "# Ignored\n");
        assertThat(site.fingerprint()).isEqualTo(withNewPage);
    }

    @Test
    void missingSourceFolderIsAnError() {
        assertThatThrownBy(() -> DocsSite.open(tempDir.resolve("missing"), "Docs", null, false))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("Docs folder not found")
                .hasMessageContaining("--source");
    }

    @Test
    void sourceFolderWithoutPagesIsAnError() throws IOException {
        write("image.png", "x");

        assertThatThrownBy(() -> DocsSite.open(tempDir, "Docs", null, false))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining("contains no Markdown pages");
    }

    private Path write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
