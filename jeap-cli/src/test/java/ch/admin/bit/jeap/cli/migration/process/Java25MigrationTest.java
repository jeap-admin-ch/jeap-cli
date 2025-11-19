package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.Migration;
import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Java25MigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testSuccessfulMigration() throws Exception {
        // Given a project directory with a pom.xml containing java.version 17
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                
                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>jeap-internal-spring-boot-parent</artifactId>
                        <version>5.14.0</version>
                    </parent>
                
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <java.version>17</java.version>
                    </properties>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // And a fake process executor that simulates successful Maven execution
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);

        // When running the Java 25 migration
        Migration migration = new Java25Migration(fakeExecutor);
        migration.migrate(tempDir);

        // Then Maven should have been executed with the correct arguments to update parent
        assertEquals(1, fakeExecutor.getExecutionCount(),
                "Should have executed one Maven command");

        FakeProcessExecutor.ExecutedCommand executedCommand = fakeExecutor.getLastExecutedCommand();
        assertEquals(List.of("mvn",
                        "versions:update-parent",
                        "-Dincludes=groupId:artifactId:type:classifier:version",
                        "-DgenerateBackupPoms=false"),
                executedCommand.command(),
                "Should execute Maven with correct parent update arguments");
        assertEquals(tempDir, executedCommand.workingDirectory(),
                "Maven should execute in the project root directory");

        // And the java.version property should be updated to 25
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<java.version>25</java.version>"),
                "java.version should be updated to 25");

        // Verify using getJavaVersion helper
        assertEquals("25", getJavaVersion(pomPath),
                "java.version should be exactly 25");
    }

    private String getJavaVersion(Path pomPath) throws Exception {
        String content = Files.readString(pomPath);
        Pattern pattern = Pattern.compile("<java\\.version\\s*>([^<]*)</java\\.version>");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
