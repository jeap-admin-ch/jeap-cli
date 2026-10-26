package ch.admin.bit.jeap.cli.docs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Opens the browser with the platform's tool: {@code open} on macOS, {@code wslview} (falling back to
 * {@code cmd.exe /c start}) in WSL, and {@code xdg-open} on other Linux systems with a graphical session.
 */
@Slf4j
@Component
public class SystemBrowserOpener implements BrowserOpener {

    private final String osName;
    private final Map<String, String> environment;
    private final Path procVersion;

    public SystemBrowserOpener() {
        this(System.getProperty("os.name", ""), System.getenv(), Path.of("/proc/version"));
    }

    SystemBrowserOpener(String osName, Map<String, String> environment, Path procVersion) {
        this.osName = osName;
        this.environment = environment;
        this.procVersion = procVersion;
    }

    @Override
    public void open(URI uri) {
        for (List<String> command : commands(uri)) {
            if (start(command)) {
                return;
            }
        }
    }

    /**
     * Returns the commands to try in order; empty if the browser cannot be opened, e.g. on a Linux system without
     * a graphical session.
     */
    List<List<String>> commands(URI uri) {
        String url = uri.toString();
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return List.of(List.of("open", url));
        }
        if (os.contains("linux")) {
            if (isWsl()) {
                return List.of(List.of("wslview", url), List.of("cmd.exe", "/c", "start", url));
            }
            if (hasText(environment.get("DISPLAY")) || hasText(environment.get("WAYLAND_DISPLAY"))) {
                return List.of(List.of("xdg-open", url));
            }
        }
        return List.of();
    }

    private boolean isWsl() {
        try {
            return Files.readString(procVersion, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("microsoft");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean start(List<String> command) {
        try {
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (IOException e) {
            log.debug("Could not open the browser with {}: {}", command.getFirst(), e.getMessage());
            return false;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
