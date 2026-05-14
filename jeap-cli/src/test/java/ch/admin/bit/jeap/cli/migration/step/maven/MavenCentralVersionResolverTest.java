package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenCentralVersionResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesVersionFromMavenRun() throws Exception {
        FakeProcessExecutor processExecutor = new FakeProcessExecutor(
                (command, workingDirectory) -> {
                    overwriteTempPomVersion(command, "2.20.0");
                    return 0;
                }
        );

        EnsureProjectDependencyManagement.MavenCentralVersionResolver resolver =
                new EnsureProjectDependencyManagement.MavenCentralVersionResolver(tempDir, processExecutor);

        Optional<String> resolved = resolver.resolveLatestVersion("commons-io", "commons-io");

        assertEquals(Optional.of("2.20.0"), resolved);
        List<String> executedCommand = processExecutor.getLastExecutedCommand().command();
        assertTrue(executedCommand.contains("org.codehaus.mojo:versions-maven-plugin:2.18.0:use-latest-releases"));
    }

    @Test
    void returnsEmptyWhenResolvedVersionIsPreRelease() throws Exception {
        FakeProcessExecutor processExecutor = new FakeProcessExecutor(
                (command, workingDirectory) -> {
                    overwriteTempPomVersion(command, "3.0.0-RC1");
                    return 0;
                }
        );

        EnsureProjectDependencyManagement.MavenCentralVersionResolver resolver =
                new EnsureProjectDependencyManagement.MavenCentralVersionResolver(tempDir, processExecutor);

        Optional<String> resolved = resolver.resolveLatestVersion("commons-io", "commons-io");

        assertTrue(resolved.isEmpty());
    }

    @Test
    void usesProjectMavenWrapperWhenAvailable() throws Exception {
        Path mvnw = tempDir.resolve("mvnw");
        Files.writeString(mvnw, "#!/usr/bin/env sh\nexit 0\n", StandardCharsets.UTF_8);
        mvnw.toFile().setExecutable(true);

        FakeProcessExecutor processExecutor = new FakeProcessExecutor(
                (command, workingDirectory) -> {
                    overwriteTempPomVersion(command, "2.20.0");
                    return 0;
                }
        );

        EnsureProjectDependencyManagement.MavenCentralVersionResolver resolver =
                new EnsureProjectDependencyManagement.MavenCentralVersionResolver(tempDir, processExecutor);

        Optional<String> resolved = resolver.resolveLatestVersion("commons-io", "commons-io");

        assertEquals(Optional.of("2.20.0"), resolved);
        String command = processExecutor.getLastExecutedCommand().command().get(0);
        assertEquals(mvnw.toAbsolutePath().toString(), command);
    }

    @Test
    void returnsEmptyWhenMavenExecutionFails() throws Exception {
        FakeProcessExecutor processExecutor = new FakeProcessExecutor(1);
        EnsureProjectDependencyManagement.MavenCentralVersionResolver resolver =
                new EnsureProjectDependencyManagement.MavenCentralVersionResolver(tempDir, processExecutor);

        Optional<String> resolved = resolver.resolveLatestVersion("commons-io", "commons-io");

        assertTrue(resolved.isEmpty());
    }

    private void overwriteTempPomVersion(List<String> command, String version) {
        int pomFlagIndex = command.indexOf("-f");
        Path pomPath = Path.of(command.get(pomFlagIndex + 1));
        try {
            String content = Files.readString(pomPath, StandardCharsets.UTF_8);
            Files.writeString(pomPath, content.replace("<version>0.0.0</version>", "<version>" + version + "</version>"),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

