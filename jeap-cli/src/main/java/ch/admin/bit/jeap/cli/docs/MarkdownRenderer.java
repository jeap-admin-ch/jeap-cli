package ch.admin.bit.jeap.cli.docs;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a Markdown page to HTML like the jEAP doc service does in its essentials: CommonMark with GitHub tables,
 * strikethrough and autolinks, YAML front matter, raw HTML escaped. Links to {@code .md} pages are rewritten to
 * {@code .html}, and fenced diagram blocks become placeholders that the browser renders (see
 * {@link DiagramNodeRenderer}).
 */
final class MarkdownRenderer {

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create(),
            YamlFrontMatterExtension.create(),
            HeadingAnchorExtension.create());
    private static final Pattern URI_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");
    private static final Pattern NUMBER_PREFIX = Pattern.compile("^(\\d+)[-_ ]+(.*)$");
    private static final Pattern SURROUNDING_QUOTES = Pattern.compile("^(['\"])(.*)\\1$");

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .nodeRendererFactory(DiagramNodeRenderer::new)
            .build();

    record RenderedPage(String title, String html) {
    }

    /**
     * Returns the title of a page: the front matter title, else the first level-1 heading, else a title derived from
     * the file name.
     */
    String title(String markdown, String fileName) {
        Node document = parser.parse(markdown);
        return title(frontMatterTitle(document), firstLevelOneHeading(document), fileName);
    }

    RenderedPage render(String markdown, String fileName) {
        Node document = parser.parse(markdown);
        String frontMatterTitle = frontMatterTitle(document);
        String heading = firstLevelOneHeading(document);
        document.accept(new LinkRewriter());
        String html = htmlRenderer.render(document);
        if (frontMatterTitle != null && heading == null) {
            // Docusaurus shows the front matter title as page heading if the page has none
            html = "<h1>" + Html.escape(frontMatterTitle) + "</h1>\n" + html;
        }
        return new RenderedPage(title(frontMatterTitle, heading, fileName), html);
    }

    /**
     * Rewrites a relative link to a Markdown page ({@code ../a/b.md#section}) to the rendered page
     * ({@code ../a/b.html#section}). Links with a URI scheme, absolute paths and fragments only stay unchanged.
     */
    static String rewriteLink(String destination) {
        if (destination == null || destination.isEmpty() || URI_SCHEME.matcher(destination).find()
                || destination.startsWith("/") || destination.startsWith("#")) {
            return destination;
        }
        int hash = destination.indexOf('#');
        String target = hash >= 0 ? destination.substring(0, hash) : destination;
        String fragment = hash >= 0 ? destination.substring(hash) : "";
        if (target.endsWith(".md")) {
            return target.substring(0, target.length() - ".md".length()) + ".html" + fragment;
        }
        return destination;
    }

    /**
     * Derives a title from a file name: {@code 3-system-context.md} becomes {@code System context}.
     */
    static String titleFromFileName(String fileName) {
        String base = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - ".md".length()) : fileName;
        Matcher matcher = NUMBER_PREFIX.matcher(base);
        return capitalizedWords(matcher.matches() ? matcher.group(2) : base, base);
    }

    /**
     * Derives a navigation label from a folder name: {@code 1-introduction-and-goals} becomes
     * {@code 1. Introduction and goals}.
     */
    static String folderLabel(String folderName) {
        Matcher matcher = NUMBER_PREFIX.matcher(folderName);
        if (matcher.matches()) {
            return matcher.group(1) + ". " + capitalizedWords(matcher.group(2), folderName);
        }
        return capitalizedWords(folderName, folderName);
    }

    /**
     * Returns the leading number of a file or folder name for sorting ({@code 2-} before {@code 10-}), or
     * {@link Integer#MAX_VALUE} if the name has none.
     */
    static int sortNumber(String name) {
        Matcher matcher = NUMBER_PREFIX.matcher(name);
        if (!matcher.matches()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String capitalizedWords(String words, String fallback) {
        String text = words.replace('-', ' ').replace('_', ' ').trim();
        if (text.isEmpty()) {
            return fallback;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String title(String frontMatterTitle, String heading, String fileName) {
        if (frontMatterTitle != null) {
            return frontMatterTitle;
        }
        if (heading != null) {
            return heading;
        }
        return titleFromFileName(fileName);
    }

    private static String frontMatterTitle(Node document) {
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        List<String> values = visitor.getData().get("title");
        if (values == null || values.isEmpty()) {
            return null;
        }
        String title = values.getFirst().trim();
        Matcher quoted = SURROUNDING_QUOTES.matcher(title);
        if (quoted.matches()) {
            title = quoted.group(2).trim();
        }
        return title.isEmpty() ? null : title;
    }

    private static String firstLevelOneHeading(Node document) {
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading && heading.getLevel() == 1) {
                StringBuilder text = new StringBuilder();
                heading.accept(new AbstractVisitor() {
                    @Override
                    public void visit(Text textNode) {
                        text.append(textNode.getLiteral());
                    }

                    @Override
                    public void visit(Code code) {
                        text.append(code.getLiteral());
                    }
                });
                String title = text.toString().trim();
                return title.isEmpty() ? null : title;
            }
        }
        return null;
    }

    private static final class LinkRewriter extends AbstractVisitor {
        @Override
        public void visit(Link link) {
            link.setDestination(rewriteLink(link.getDestination()));
            visitChildren(link);
        }
    }
}
