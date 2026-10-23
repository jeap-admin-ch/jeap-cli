package ch.admin.bit.jeap.cli.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemBrowserOpenerTest {

    private static final URI URL = URI.create("http://127.0.0.1:4000/");

    @TempDir
    Path tempDir;

    @Test
    void macOsUsesOpen() {
        SystemBrowserOpener opener = new SystemBrowserOpener("Mac OS X", Map.of(), tempDir.resolve("none"));

        assertThat(opener.commands(URL)).containsExactly(List.of("open", "http://127.0.0.1:4000/"));
    }

    @Test
    void wslUsesWslviewWithWindowsFallback() throws IOException {
        Path procVersion = Files.writeString(tempDir.resolve("version"),
                "Linux version 5.15.153.1-microsoft-standard-WSL2 (root@1c602f52c2e4)");
        SystemBrowserOpener opener = new SystemBrowserOpener("Linux", Map.of(), procVersion);

        assertThat(opener.commands(URL)).containsExactly(
                List.of("wslview", "http://127.0.0.1:4000/"),
                List.of("cmd.exe", "/c", "start", "http://127.0.0.1:4000/"));
    }

    @Test
    void linuxWithGraphicalSessionUsesXdgOpen() throws IOException {
        Path procVersion = Files.writeString(tempDir.resolve("version"), "Linux version 6.8.0-45-generic");

        assertThat(new SystemBrowserOpener("Linux", Map.of("DISPLAY", ":0"), procVersion).commands(URL))
                .containsExactly(List.of("xdg-open", "http://127.0.0.1:4000/"));
        assertThat(new SystemBrowserOpener("Linux", Map.of("WAYLAND_DISPLAY", "wayland-0"), procVersion).commands(URL))
                .containsExactly(List.of("xdg-open", "http://127.0.0.1:4000/"));
    }

    @Test
    void linuxWithoutGraphicalSessionOpensNothing() throws IOException {
        Path procVersion = Files.writeString(tempDir.resolve("version"), "Linux version 6.8.0-45-generic");

        assertThat(new SystemBrowserOpener("Linux", Map.of(), procVersion).commands(URL)).isEmpty();
    }

    @Test
    void otherSystemsOpenNothing() {
        assertThat(new SystemBrowserOpener("Windows 11", Map.of(), tempDir.resolve("none")).commands(URL)).isEmpty();
    }
}
