package ch.admin.bit.jeap.cli.commands;

import ch.admin.bit.jeap.cli.docs.BrowserOpener;
import ch.admin.bit.jeap.cli.docs.DocsBuilder;
import ch.admin.bit.jeap.cli.docs.DocsException;
import ch.admin.bit.jeap.cli.docs.DocsPreviewServer;
import ch.admin.bit.jeap.cli.docs.DocsSite;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

@Component
public class DocsCommands {

    private static final String GROUP = "Docs";

    private final BrowserOpener browserOpener;

    public DocsCommands(BrowserOpener browserOpener) {
        this.browserOpener = browserOpener;
    }

    @Command(name = {"docs", "preview"}, group = GROUP,
            description = "Preview the Markdown docs in the browser, reloading on changes (Ctrl+C to stop)")
    public void preview(
            @Option(longName = "source", defaultValue = "docs",
                    description = "Docs folder, relative to the working directory") String source,
            @Option(longName = "port", defaultValue = "4000",
                    description = "Port on 127.0.0.1; a free port is used if it is taken") int port,
            @Option(longName = "no-browser", defaultValue = "false",
                    description = "Only print the link, do not open the browser") boolean noBrowser,
            @Option(longName = "title",
                    description = "Site title (default: name of the git repository folder)") String title) throws Exception {
        if (port < 0 || port > 65535) {
            throw new DocsException("Invalid port " + port + ". Use a port between 1 and 65535.");
        }
        Path sourcePath = Path.of(source);
        DocsSite site = DocsSite.open(sourcePath, titleOrDefault(title),
                "Local preview of " + sourcePath.toAbsolutePath().normalize(), true);
        DocsPreviewServer server = DocsPreviewServer.start(site, port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "docs-preview-shutdown"));
        if (port != 0 && server.port() != port) {
            System.out.println("Port " + port + " is in use, using port " + server.port() + " instead.");
        }
        System.out.println("Docs preview: " + server.uri() + " (Ctrl+C to stop)");
        if (!noBrowser) {
            browserOpener.open(server.uri());
        }
        // serve until the process is interrupted; the shutdown hook stops the server
        new CountDownLatch(1).await();
    }

    @Command(name = {"docs", "build"}, group = GROUP,
            description = "Write the Markdown docs as a static HTML site, e.g. for a pull request preview")
    public void build(
            @Option(longName = "source", defaultValue = "docs",
                    description = "Docs folder, relative to the working directory") String source,
            @Option(longName = "output", required = true,
                    description = "Output folder; it is emptied first") String output,
            @Option(longName = "title",
                    description = "Site title (default: name of the git repository folder)") String title,
            @Option(longName = "banner",
                    description = "Text shown at the top of every page, e.g. the branch of the preview") String banner) throws Exception {
        DocsSite site = DocsSite.open(Path.of(source), titleOrDefault(title), banner, false);
        DocsBuilder.Result result = DocsBuilder.build(site, Path.of(output));
        System.out.println("Wrote " + result.pages() + " page(s) and " + result.staticFiles()
                + " other file(s) to " + result.output());
    }

    static String titleOrDefault(String title) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return defaultTitle(Path.of("").toAbsolutePath());
    }

    /**
     * Returns the name of the git repository folder containing the working directory, else the name of the
     * working directory.
     */
    static String defaultTitle(Path workingDirectory) {
        for (Path folder = workingDirectory; folder != null; folder = folder.getParent()) {
            if (Files.exists(folder.resolve(".git"))) {
                return name(folder);
            }
        }
        return name(workingDirectory);
    }

    private static String name(Path folder) {
        Path fileName = folder.getFileName();
        return fileName == null ? "Docs" : fileName.toString();
    }
}
