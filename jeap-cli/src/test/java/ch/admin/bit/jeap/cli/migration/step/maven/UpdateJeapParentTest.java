package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateJeapParentTest {

    @TempDir
    Path tempDir;

    @Test
    void testExcludesQualifiedVersionsByDefault() throws Exception {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);

        new UpdateJeapParent(tempDir, fakeExecutor).execute();

        assertEquals(1, fakeExecutor.getExecutionCount());
        FakeProcessExecutor.ExecutedCommand executed = fakeExecutor.getLastExecutedCommand();
        assertEquals(List.of("mvn",
                        "-ntp",
                        MavenPlugin.VERSIONS.goal("update-parent"),
                        "-Dincludes=ch.admin.bit.jeap",
                        "-DgenerateBackupPoms=false",
                        "-Dversions.ignoredVersions=" + UpdateJeapParent.IGNORE_QUALIFIED_VERSIONS),
                executed.command());
    }

    @Test
    void testIncludesQualifiedVersionsWhenRequested() throws Exception {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);

        new UpdateJeapParent(tempDir, fakeExecutor, true).execute();

        FakeProcessExecutor.ExecutedCommand executed = fakeExecutor.getLastExecutedCommand();
        assertEquals(List.of("mvn",
                        "-ntp",
                        MavenPlugin.VERSIONS.goal("update-parent"),
                        "-Dincludes=ch.admin.bit.jeap",
                        "-DgenerateBackupPoms=false"),
                executed.command());
    }

    @Test
    void testThrowsOnMavenFailure() {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(1);

        assertThrows(IOException.class,
                () -> new UpdateJeapParent(tempDir, fakeExecutor).execute());
    }

    @Test
    void testKeepsCurrentParentOnMavenFailureWhenFallbackIsEnabled() throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        String baselinePom = "<project><version>9.1.0</version></project>";
        Files.writeString(pomPath, baselinePom);
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor((command, workingDirectory) -> {
            try {
                Files.writeString(pomPath, "<project><version>partially-updated</version></project>");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return 1;
        });

        UpdateJeapParent.latestStableWithFallback(tempDir, fakeExecutor).execute();

        assertEquals(1, fakeExecutor.getExecutionCount());
        assertEquals(baselinePom, Files.readString(pomPath));
    }

    @Test
    void testStepName() {
        UpdateJeapParent step = new UpdateJeapParent(tempDir, new FakeProcessExecutor(0));
        assertEquals("Update Jeap Parent", step.name());
    }
}
