package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.Migration;
import ch.admin.bit.jeap.cli.migration.step.maven.MavenPlugin;
import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringBoot4MigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testSuccessfulMigration() throws Exception {
        // Given a project directory with a pom.xml
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>jeap-internal-spring-boot-parent</artifactId>
                        <version>5.14.0</version>
                    </parent>
                
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // And a fake process executor that simulates successful Maven execution
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);

        // When running the Spring Boot 4 migration
        Migration migration = new SpringBoot4Migration(fakeExecutor);
        migration.migrate(tempDir);

        // Then four Maven commands should have been executed
        assertEquals(4, fakeExecutor.getExecutionCount(),
                "Should have executed four Maven commands");

        // First command: update parent
        FakeProcessExecutor.ExecutedCommand firstCommand = fakeExecutor.getExecutedCommands().get(0);
        assertEquals(List.of("mvn",
                        MavenPlugin.VERSIONS.goal("update-parent"),
                        "-Dincludes=ch.admin.bit.jeap",
                        "-DgenerateBackupPoms=false"),
                firstCommand.command(),
                "First command should update the jEAP parent");
        assertEquals(tempDir, firstCommand.workingDirectory(),
                "Maven should execute in the project root directory");

        // Second command: update jEAP dependency versions
        FakeProcessExecutor.ExecutedCommand secondCommand = fakeExecutor.getExecutedCommands().get(1);
        assertEquals(List.of("mvn",
                        MavenPlugin.VERSIONS.goal("use-latest-releases"),
                        "-Dincludes=ch.admin.bit.jeap",
                        "-DgenerateBackupPoms=false"),
                secondCommand.command(),
                "Second command should update jEAP dependency versions");
        assertEquals(tempDir, secondCommand.workingDirectory(),
                "Maven should execute in the project root directory");

        // Third command: OpenRewrite Spring Boot 4 migration
        FakeProcessExecutor.ExecutedCommand thirdCommand = fakeExecutor.getExecutedCommands().get(2);
        assertEquals(List.of("mvn",
                        "-U",
                        MavenPlugin.OPENREWRITE.goal("run"),
                        "-Drewrite.recipeArtifactCoordinates=ch.admin.bit.jeap.openrewrite.recipe:jeap-rewrite-recipes:1.5.0,org.openrewrite.recipe:rewrite-spring:RELEASE",
                        "-Drewrite.activeRecipes=ch.admin.bit.jeap.openrewrite.recipe.UpgradeSpringBoot_4_0_NoOtherMigrations",
                        "-Drewrite.exportDatatables=true"),
                thirdCommand.command(),
                "Third command should run the OpenRewrite Spring Boot 4 recipe");
        assertEquals(tempDir, thirdCommand.workingDirectory(),
                "Maven should execute in the project root directory");

        // Fourth command: Maven install after OpenRewrite
        FakeProcessExecutor.ExecutedCommand fourthCommand = fakeExecutor.getExecutedCommands().get(3);
        assertEquals(List.of("mvn", "install"),
                fourthCommand.command(),
                "Fourth command should run Maven install");
        assertEquals(tempDir, fourthCommand.workingDirectory(),
                "Maven should execute in the project root directory");
    }

    @Test
    void testAutoFixRetriesMigrationAfterMavenFailure() throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, minimalPom());

        AtomicInteger executionCounter = new AtomicInteger();
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(
                (cmd, dir) -> executionCounter.incrementAndGet() == 1 ? 1 : 0,
                (cmd, dir) -> "[ERROR] symbol: class WebMvcTest");

        RecordingAutoFixer autoFixer = new RecordingAutoFixer(true);
        SpringBoot4Migration migration = new SpringBoot4Migration(fakeExecutor, autoFixer);

        migration.migrate(tempDir, true, 2);

        assertEquals(5, fakeExecutor.getExecutionCount(),
                "First run fails at first Maven step, second run should complete all four Maven steps");
        assertEquals(1, autoFixer.invocations,
                "Auto-fixer should be invoked exactly once for one failed attempt");
        assertEquals(1, autoFixer.preparations,
                "Auto-fixer setup should be validated once when auto-fix mode is enabled");
    }

    @Test
    void testAutoFixResumesFromFailedStepWithoutRepeatingCompletedSteps() throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, minimalPom());

        AtomicInteger rewriteAttempts = new AtomicInteger();
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor((cmd, dir) -> {
            boolean isOpenRewrite = cmd.contains(MavenPlugin.OPENREWRITE.goal("run"));
            if (isOpenRewrite && rewriteAttempts.incrementAndGet() == 1) {
                return 1;
            }
            return 0;
        }, (cmd, dir) -> "[ERROR] rewrite failure");

        RecordingAutoFixer autoFixer = new RecordingAutoFixer(true);
        SpringBoot4Migration migration = new SpringBoot4Migration(fakeExecutor, autoFixer);

        migration.migrate(tempDir, true, 2);

        assertEquals(5, fakeExecutor.getExecutionCount(),
                "After OpenRewrite failure, retry should continue from OpenRewrite and not rerun completed steps");
        assertEquals(1, autoFixer.invocations, "Auto-fixer should be invoked once");
        assertEquals(2, fakeExecutor.getExecutedCommands().stream()
                        .filter(command -> command.command().contains(MavenPlugin.OPENREWRITE.goal("run")))
                        .count(),
                "OpenRewrite should run once per attempt");
        assertEquals(1, fakeExecutor.getExecutedCommands().stream()
                        .filter(command -> command.command().equals(List.of("mvn", MavenPlugin.VERSIONS.goal("update-parent"),
                                "-Dincludes=ch.admin.bit.jeap", "-DgenerateBackupPoms=false")))
                        .count(),
                "Update parent step must not be repeated after it already succeeded");
    }

    @Test
    void testAutoFixStopsWhenFixerDeclinesRetry() throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, minimalPom());

        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(1, "[ERROR] compile failure");
        RecordingAutoFixer autoFixer = new RecordingAutoFixer(false);
        SpringBoot4Migration migration = new SpringBoot4Migration(fakeExecutor, autoFixer);

        assertThrows(Exception.class, () -> migration.migrate(tempDir, true, 2));
        assertEquals(1, autoFixer.invocations,
                "Auto-fixer should be invoked once before aborting");
        assertEquals(1, autoFixer.preparations,
                "Auto-fixer setup should be validated once when auto-fix mode is enabled");
    }

    @Test
    void testAutoFixAbortsWhenCopilotCliSetupFails() throws Exception {
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, minimalPom());

        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);
        RecordingAutoFixer autoFixer = new RecordingAutoFixer(true, false);
        SpringBoot4Migration migration = new SpringBoot4Migration(fakeExecutor, autoFixer);

        assertThrows(IllegalStateException.class, () -> migration.migrate(tempDir, true, 2));
        assertEquals(1, autoFixer.preparations, "Auto-fixer setup must be attempted once");
        assertEquals(0, autoFixer.invocations, "No fix attempt should happen when setup fails");
        assertEquals(0, fakeExecutor.getExecutionCount(), "Migration should abort before Maven execution");
    }

    private static String minimalPom() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>jeap-internal-spring-boot-parent</artifactId>
                        <version>5.14.0</version>
                    </parent>

                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """;
    }

    private static class RecordingAutoFixer implements MavenFailureAutoFixer {
        private final boolean result;
        private final boolean preparationResult;
        private int invocations;
        private int preparations;

        private RecordingAutoFixer(boolean result) {
            this(result, true);
        }

        private RecordingAutoFixer(boolean result, boolean preparationResult) {
            this.result = result;
            this.preparationResult = preparationResult;
        }

        @Override
        public boolean prepare(Path root) {
            preparations++;
            return preparationResult;
        }

        @Override
        public boolean tryFix(Path root, ch.admin.bit.jeap.cli.migration.step.maven.MavenCommandException failure,
                              int attempt, int maxRetries) {
            invocations++;
            return result;
        }
    }
}
