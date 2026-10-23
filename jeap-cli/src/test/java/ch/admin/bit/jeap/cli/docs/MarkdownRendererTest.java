package ch.admin.bit.jeap.cli.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void titleFromUnquotedFrontMatter() {
        String markdown = """
                ---
                title: Deployment view
                description: Where it runs
                ---
                # Heading
                """;
        assertThat(renderer.title(markdown, "7-deployment.md")).isEqualTo("Deployment view");
    }

    @Test
    void titleFromQuotedFrontMatter() {
        assertThat(renderer.title("---\ntitle: \"Quoted: title\"\n---\ntext\n", "a.md")).isEqualTo("Quoted: title");
        assertThat(renderer.title("---\ntitle: 'Single quoted'\n---\ntext\n", "a.md")).isEqualTo("Single quoted");
    }

    @Test
    void titleFromFirstLevelOneHeadingIncludingCode() {
        assertThat(renderer.title("Intro\n\n## Not this\n\n# The `jeap` CLI\n", "a.md")).isEqualTo("The jeap CLI");
    }

    @Test
    void titleFromFileNameWithoutNumberPrefix() {
        assertThat(renderer.title("No heading here.\n", "3-system_context-view.md")).isEqualTo("System context view");
        assertThat(renderer.title("", "glossary.md")).isEqualTo("Glossary");
    }

    @Test
    void frontMatterTitleIsRenderedAsHeadingIfThePageHasNone() {
        MarkdownRenderer.RenderedPage page = renderer.render("---\ntitle: Quality <goals>\n---\nText\n", "a.md");

        assertThat(page.title()).isEqualTo("Quality <goals>");
        assertThat(page.html()).startsWith("<h1>Quality &lt;goals&gt;</h1>");
    }

    @Test
    void frontMatterTitleIsNotAddedIfThePageHasAHeading() {
        MarkdownRenderer.RenderedPage page = renderer.render("---\ntitle: Front\n---\n# Own heading\n", "a.md");

        assertThat(page.title()).isEqualTo("Front");
        assertThat(page.html()).doesNotContain("<h1>Front</h1>").contains(">Own heading</h1>");
    }

    @Test
    void rawHtmlIsEscaped() {
        String html = renderer.render("Inline <b>bold</b>\n\n<div onclick=\"x()\">block</div>\n", "a.md").html();

        assertThat(html).contains("&lt;b&gt;bold&lt;/b&gt;").contains("&lt;div onclick=").doesNotContain("<b>", "<div onclick");
    }

    @Test
    void rendersTablesStrikethroughAndAutolinks() {
        String html = renderer.render("""
                | A | B |
                |---|---|
                | 1 | 2 |

                ~~old~~ see https://www.admin.ch
                """, "a.md").html();

        assertThat(html)
                .contains("<table>", "<td>1</td>")
                .contains("<del>old</del>")
                .contains("<a href=\"https://www.admin.ch\">https://www.admin.ch</a>");
    }

    @Test
    void headingsGetGitHubLikeIds() {
        String html = renderer.render("# Title\n\n## Quality Goals!\n\n## Quality Goals!\n", "a.md").html();

        assertThat(html)
                .contains("<h2 id=\"quality-goals\">")
                .contains("<h2 id=\"quality-goals-1\">");
    }

    @Test
    void rewritesLinksToMarkdownPagesButNotImages() {
        String html = renderer.render("""
                [Page](../2-diagrams/diagrams.md#plantuml) [Other](other.md) [Site](https://example.com/a.md)
                ![Image](images/diagram.md.png)
                """, "a.md").html();

        assertThat(html)
                .contains("href=\"../2-diagrams/diagrams.html#plantuml\"")
                .contains("href=\"other.html\"")
                .contains("href=\"https://example.com/a.md\"")
                .contains("src=\"images/diagram.md.png\"");
    }

    @ParameterizedTest
    @CsvSource({
            "page.md, page.html",
            "../a/b.md, ../a/b.html",
            "b.md#section, b.html#section",
            "#section, #section",
            "/absolute.md, /absolute.md",
            "https://example.com/x.md, https://example.com/x.md",
            "mailto:a@b.ch, mailto:a@b.ch",
            "image.png, image.png",
            "folder/, folder/"
    })
    void rewriteLink(String destination, String expected) {
        assertThat(MarkdownRenderer.rewriteLink(destination)).isEqualTo(expected);
    }

    @Test
    void mermaidFenceBecomesMermaidBlock() {
        String html = renderer.render("```mermaid\nflowchart LR\n  A --> B\n```\n", "a.md").html();

        assertThat(html).contains("<pre class=\"mermaid\">flowchart LR\n  A --&gt; B\n</pre>");
    }

    @Test
    void plantUmlFencesBecomeDiagramPlaceholders() {
        String source = "@startuml\nA -> \"B\" : <hi>\n@enduml\n";
        String escaped = "@startuml\nA -&gt; &quot;B&quot; : &lt;hi&gt;\n@enduml\n";

        for (String language : new String[]{"plantuml", "puml", "PlantUML"}) {
            String html = renderer.render("```" + language + "\n" + source + "```\n", "a.md").html();
            assertThat(html).contains("<div class=\"diagram plantuml\" data-source=\"" + escaped + "\"><pre>"
                    + escaped + "</pre></div>");
        }
    }

    @Test
    void graphvizFencesBecomeDiagramPlaceholders() {
        for (String language : new String[]{"dot", "graphviz", "gv"}) {
            String html = renderer.render("```" + language + " {title=x}\ndigraph { a -> b }\n```\n", "a.md").html();
            assertThat(html).contains("<div class=\"diagram graphviz\" data-source=\"digraph { a -&gt; b }\n\">"
                    + "<pre>digraph { a -&gt; b }\n</pre></div>");
        }
    }

    @Test
    void ordinaryFencesStayCodeBlocks() {
        assertThat(renderer.render("```java\nint a = 1 < 2;\n```\n", "a.md").html())
                .contains("<pre><code class=\"language-java\">int a = 1 &lt; 2;\n</code></pre>");
        assertThat(renderer.render("```\nplain\n```\n", "a.md").html())
                .contains("<pre><code>plain\n</code></pre>");
    }

    @Test
    void indentedCodeBlocksAreUnchanged() {
        assertThat(renderer.render("Text\n\n    indented code\n", "a.md").html())
                .contains("<pre><code>indented code\n</code></pre>");
    }

    @ParameterizedTest
    @CsvSource({
            "1-introduction-and-goals, 1. Introduction and goals",
            "10_cross_cutting, 10. Cross cutting",
            "glossary, Glossary",
            "adr, Adr"
    })
    void folderLabel(String folderName, String expected) {
        assertThat(MarkdownRenderer.folderLabel(folderName)).isEqualTo(expected);
    }

    @Test
    void sortNumber() {
        assertThat(MarkdownRenderer.sortNumber("2-constraints")).isLessThan(MarkdownRenderer.sortNumber("10-quality"));
        assertThat(MarkdownRenderer.sortNumber("glossary.md")).isEqualTo(Integer.MAX_VALUE);
        assertThat(MarkdownRenderer.sortNumber("99999999999-huge")).isEqualTo(Integer.MAX_VALUE);
    }
}
