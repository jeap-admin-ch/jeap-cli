package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.Migration;
import ch.admin.bit.jeap.cli.migration.step.maven.MavenPlugin;
import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        // And a fake process executor that fails only the optional latest-parent lookup
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor((command, workingDirectory) ->
                command.contains(MavenPlugin.VERSIONS.goal("update-parent")) ? 1 : 0);

        // When running the Spring Boot 4 migration
        Migration migration = new SpringBoot4Migration(fakeExecutor);
        migration.migrate(tempDir);

        assertTrue(Files.readString(pomPath).contains("<version>9.1.0</version>"),
                "Should keep the pinned internal jEAP parent when latest-version resolution fails");

        // Then three Maven commands should have been executed
        assertEquals(3, fakeExecutor.getExecutionCount(),
                "Should have executed three Maven commands");

        // First command: update the jEAP parent to the latest stable release
        FakeProcessExecutor.ExecutedCommand firstCommand = fakeExecutor.getExecutedCommands().get(0);
        assertEquals(List.of("mvn",
                        "-ntp",
                        MavenPlugin.VERSIONS.goal("update-parent"),
                        "-Dincludes=ch.admin.bit.jeap",
                        "-DgenerateBackupPoms=false",
                        "-Dversions.ignoredVersions=.*-[a-zA-Z].*"),
                firstCommand.command(),
                "First command should update the jEAP parent");
        assertEquals(tempDir, firstCommand.workingDirectory(),
                "Maven should execute in the project root directory");

        // Second command: update jEAP dependency versions
        FakeProcessExecutor.ExecutedCommand secondCommand = fakeExecutor.getExecutedCommands().get(1);
        assertEquals(List.of("mvn",
                        "-ntp",
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
                        "-ntp",
                        MavenPlugin.OPENREWRITE.goal("run"),
                        "-Drewrite.recipeArtifactCoordinates=ch.admin.bit.jeap.openrewrite.recipe:jeap-rewrite-recipes:1.5.6,org.openrewrite.recipe:rewrite-spring:6.30.4",
                        "-Drewrite.activeRecipes=ch.admin.bit.jeap.openrewrite.recipe.UpgradeSpringBoot_4_0_NoOtherMigrations",
                        "-Drewrite.exportDatatables=true",
                        "-Dmaven.compiler.failOnError=false"),
                thirdCommand.command(),
                "Third command should run the OpenRewrite Spring Boot 4 recipe");
        assertEquals(tempDir, thirdCommand.workingDirectory(),
                "Maven should execute in the project root directory");
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


}
