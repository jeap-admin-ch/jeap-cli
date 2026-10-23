package ch.admin.bit.jeap.cli.docs;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders fenced code blocks. Diagram blocks become placeholders that {@code preview.js} renders in the browser; the
 * visible {@code <pre>} keeps the source readable until then and without JavaScript. As this replaces the core
 * rendering of fenced code blocks, it also writes the ordinary ones.
 */
final class DiagramNodeRenderer implements NodeRenderer {

    private static final Set<String> MERMAID = Set.of("mermaid");
    private static final Set<String> PLANTUML = Set.of("plantuml", "puml");
    private static final Set<String> GRAPHVIZ = Set.of("dot", "graphviz", "gv");

    private final HtmlWriter html;

    DiagramNodeRenderer(HtmlNodeRendererContext context) {
        this.html = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(FencedCodeBlock.class);
    }

    @Override
    public void render(Node node) {
        FencedCodeBlock block = (FencedCodeBlock) node;
        String language = language(block.getInfo());
        String source = block.getLiteral();
        html.line();
        if (MERMAID.contains(language)) {
            html.tag("pre", Map.of("class", "mermaid"));
            html.text(source);
            html.tag("/pre");
        } else if (PLANTUML.contains(language)) {
            diagramPlaceholder("plantuml", source);
        } else if (GRAPHVIZ.contains(language)) {
            diagramPlaceholder("graphviz", source);
        } else {
            html.tag("pre");
            html.tag("code", language.isEmpty() ? Map.of() : Map.of("class", "language-" + language));
            html.text(source);
            html.tag("/code");
            html.tag("/pre");
        }
        html.line();
    }

    private void diagramPlaceholder(String kind, String source) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("class", "diagram " + kind);
        attributes.put("data-source", source);
        html.tag("div", attributes);
        html.tag("pre");
        html.text(source);
        html.tag("/pre");
        html.tag("/div");
    }

    private static String language(String info) {
        if (info == null || info.isBlank()) {
            return "";
        }
        return info.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }
}
