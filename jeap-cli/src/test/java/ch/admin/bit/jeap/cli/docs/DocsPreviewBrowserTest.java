package ch.admin.bit.jeap.cli.docs;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.ColorScheme;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the sample diagrams in Chrome, so that an update of the bundled JavaScript libraries (mermaid,
 * {@code @plantuml/core} with Viz.js and the PlantUML themes, DOMPurify) that breaks the preview fails the build. The
 * other docs tests only see the HTML placeholders and the files, not whether the libraries still render them.
 * <p>
 * Needs an installed Chrome, so it only runs with {@code -Ddocs.browser-test=true}, which the Linux build in
 * {@code build-and-release.yml} sets.
 */
@EnabledIfSystemProperty(named = "docs.browser-test", matches = "true")
class DocsPreviewBrowserTest {

    private static final String ASSETS_PATH = "/" + DocsAssets.ASSETS_FOLDER;
    // 2-diagrams/diagrams.md of the sample docs: one Mermaid, three PlantUML (one with a theme, one broken) and two
    // Graphviz diagrams (one with a javascript: link)
    private static final int DIAGRAMS = 6;

    private static Playwright playwright;
    private static Browser browser;

    private DocsPreviewServer server;

    @BeforeAll
    static void launchChrome() {
        // The installed Chrome instead of the browsers Playwright would download otherwise
        playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
    }

    @AfterAll
    static void closeChrome() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void startServer() throws Exception {
        server = DocsPreviewServer.start(DocsSite.open(DocsSiteTest.sampleDocs(), "Sample", "Browser test", false), 0);
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @ParameterizedTest
    @EnumSource(value = ColorScheme.class, names = {"LIGHT", "DARK"})
    void rendersTheDiagramsOfEveryBundledLibrary(ColorScheme colorScheme) {
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setColorScheme(colorScheme))) {
            Page page = context.newPage();
            // Chrome asks for a favicon the preview does not have; its 404 would count as an error below
            page.route("**/favicon.ico", route -> route.fulfill(new Route.FulfillOptions().setStatus(204)));
            List<String> errors = new CopyOnWriteArrayList<>();
            page.onPageError(errors::add);
            page.onConsoleMessage(message -> {
                if ("error".equals(message.type())) {
                    errors.add(message.text() + " (" + message.location() + ")");
                }
            });
            Map<String, Integer> assets = new ConcurrentHashMap<>();
            page.onResponse(response -> {
                String path = URI.create(response.url()).getPath();
                if (path.startsWith(ASSETS_PATH)) {
                    assets.put(path.substring(ASSETS_PATH.length()), response.status());
                }
            });

            page.navigate(server.uri().resolve("2-diagrams/diagrams.html").toString());
            // A diagram is done once it holds an SVG or shows the preview's error box. A library that fails before
            // that leaves the wait to time out, so the errors it logged are the better failure message.
            try {
                page.waitForFunction("count => { const diagrams = [...document.querySelectorAll('div.diagram')];"
                                + " return diagrams.length === count"
                                + " && diagrams.every(d => d.querySelector('svg') || d.classList.contains('failed')); }",
                        DIAGRAMS, new Page.WaitForFunctionOptions().setTimeout(30_000));
            } catch (TimeoutError e) {
                assertThat(errors).as("JavaScript errors while rendering the diagrams").isEmpty();
                throw e;
            }

            assertThat(errors).as("JavaScript errors").isEmpty();
            assertThat(assets).as("loaded preview assets").containsAllEntriesOf(Map.of(
                    "vendor/mermaid.min.js", 200,
                    "vendor/plantuml.js", 200,
                    "vendor/viz-global.js", 200,
                    "vendor/themes.js", 200,
                    "vendor/purify.min.js", 200));

            assertRendered(page.locator("div.diagram.mermaid-diagram"), 1);
            // Includes the broken diagram: PlantUML shows its own error image for a syntax error
            assertRendered(page.locator("div.diagram.plantuml"), 3);
            assertRendered(page.locator("div.diagram.graphviz"), 2);
            assertThat(page.evaluate("() => typeof globalThis.PLANTUML_THEMES")).as("PlantUML themes").isEqualTo("object");
            assertScriptLinkRemoved(page);
        }
    }

    private static void assertRendered(Locator diagrams, int count) {
        assertThat(diagrams.count()).isEqualTo(count);
        for (Locator diagram : diagrams.all()) {
            assertThat(diagram.getAttribute("class")).doesNotContain("failed");
            assertThat(diagram.locator("svg").count()).as("SVG in %s", diagram.getAttribute("class")).isPositive();
        }
    }

    /**
     * Graphviz writes the javascript: link into its SVG; DOMPurify has to remove it before the SVG is inserted.
     */
    private static void assertScriptLinkRemoved(Page page) {
        Locator diagram = page.locator("div.diagram.graphviz").filter(new Locator.FilterOptions().setHasText("Click me"));
        Object unsanitized = page.evaluate("async source => (await Viz.instance()).renderString(source, {format: 'svg'})",
                diagram.getAttribute("data-source"));
        assertThat((String) unsanitized).contains("javascript:alert(1)");
        assertThat(diagram.innerHTML()).contains("Click me").doesNotContain("javascript:");
    }
}
