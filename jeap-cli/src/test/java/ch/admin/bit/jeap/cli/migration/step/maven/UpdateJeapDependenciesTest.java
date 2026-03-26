package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateJeapDependenciesTest {

    @TempDir
    Path tempDir;

    @Test
    void testExcludesQualifiedVersionsByDefault() throws Exception {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);

        new UpdateJeapDependencies(tempDir, fakeExecutor).execute();

        assertEquals(1, fakeExecutor.getExecutionCount());
        FakeProcessExecutor.ExecutedCommand executed = fakeExecutor.getLastExecutedCommand();
        assertEquals(List.of("mvn",
                        MavenPlugin.VERSIONS.goal("use-latest-releases"),
                        "-Dincludes=ch.admin.bit.jeap",
                        "-DgenerateBackupPoms=false",
                        "-Dversions.ignoredVersions=" + UpdateJeapParent.IGNORE_QUALIFIED_VERSIONS),
                executed.command());
    }

    @Test
    void testIncludesQualifiedVersionsWhenRequested() throws Exception {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);

        new UpdateJeapDependencies(tempDir, fakeExecutor, true).execute();

        FakeProcessExecutor.ExecutedCommand executed = fakeExecutor.getLastExecutedCommand();
        assertEquals(List.of("mvn",
                        MavenPlugin.VERSIONS.goal("use-latest-releases"),
                        "-Dincludes=ch.admin.bit.jeap",
                        "-DgenerateBackupPoms=false"),
                executed.command());
    }

    @Test
    void testThrowsOnMavenFailure() {
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(1);

        assertThrows(IOException.class,
                () -> new UpdateJeapDependencies(tempDir, fakeExecutor).execute());
    }

    @Test
    void testStepName() {
        UpdateJeapDependencies step = new UpdateJeapDependencies(tempDir, new FakeProcessExecutor(0));
        assertEquals("Update Jeap Dependencies", step.name());
    }
}
