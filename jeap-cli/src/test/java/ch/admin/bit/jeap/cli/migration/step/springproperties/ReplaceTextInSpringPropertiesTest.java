package ch.admin.bit.jeap.cli.migration.step.springproperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReplaceTextInSpringPropertiesTest {

    @TempDir
    Path tempDir;

    private static final String OLD_TEXT = "aws-secretsmanager:";
    private static final String NEW_TEXT = "jeap-aws-secretsmanager:";

    @Test
    void testReplacesAwsPrefixInApplicationYml() throws Exception {
        String content = """
                spring:
                  config:
                    import:
                      - "jeap-app-config-aws:"
                      - aws-secretsmanager:jme-aws-config-service?prefix=aws.secrets.
                      - aws-secretsmanager:shared/credential-context?prefix=aws.secrets-shared.credential-context.
                      - optional:aws-secretsmanager:jme-aws-db-example?prefix=aws.secrets-other.
                """;

        Path file = tempDir.resolve("application.yml");
        Files.writeString(file, content);

        new ReplaceTextInSpringProperties(tempDir, OLD_TEXT, NEW_TEXT).execute();

        String updated = Files.readString(file);
        // Only "aws-secretsmanager:" (without "jeap-" prefix) should be gone
        assertFalse(updated.contains("- aws-secretsmanager:"), "Old prefix should be replaced");
        assertFalse(updated.contains("optional:aws-secretsmanager:"), "Old prefix in optional: should be replaced");
        assertTrue(updated.contains("jeap-aws-secretsmanager:jme-aws-config-service"));
        assertTrue(updated.contains("jeap-aws-secretsmanager:shared/credential-context"));
        assertTrue(updated.contains("optional:jeap-aws-secretsmanager:jme-aws-db-example"));
        // jeap-app-config-aws: should be untouched
        assertTrue(updated.contains("jeap-app-config-aws:"));
    }

    @Test
    void testReplacesAwsPrefixInApplicationYaml() throws Exception {
        String content = "spring:\n  config:\n    import:\n      - aws-secretsmanager:my-secret\n";
        Path file = tempDir.resolve("application.yaml");
        Files.writeString(file, content);

        new ReplaceTextInSpringProperties(tempDir, OLD_TEXT, NEW_TEXT).execute();

        String updated = Files.readString(file);
        assertTrue(updated.contains("jeap-aws-secretsmanager:my-secret"));
        assertFalse(updated.contains("- aws-secretsmanager:"));
    }

    @Test
    void testReplacesAwsPrefixInApplicationProperties() throws Exception {
        String content = "spring.config.import=aws-secretsmanager:my-secret\n";
        Path file = tempDir.resolve("application.properties");
        Files.writeString(file, content);

        new ReplaceTextInSpringProperties(tempDir, OLD_TEXT, NEW_TEXT).execute();

        String updated = Files.readString(file);
        assertTrue(updated.contains("jeap-aws-secretsmanager:my-secret"));
        assertFalse(updated.contains("=aws-secretsmanager:"));
    }

    @Test
    void testReplacesInProfileSpecificFile() throws Exception {
        String content = "spring.config.import=aws-secretsmanager:my-secret\n";
        Path file = tempDir.resolve("application-dev.yml");
        Files.writeString(file, content);

        new ReplaceTextInSpringProperties(tempDir, OLD_TEXT, NEW_TEXT).execute();

        String updated = Files.readString(file);
        assertTrue(updated.contains("jeap-aws-secretsmanager:my-secret"));
    }

    @Test
    void testSearchesRecursively() throws Exception {
        Path subDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(subDir);
        String content = "spring.config.import=aws-secretsmanager:my-secret\n";
        Path file = subDir.resolve("application.yml");
        Files.writeString(file, content);

        new ReplaceTextInSpringProperties(tempDir, OLD_TEXT, NEW_TEXT).execute();

        String updated = Files.readString(file);
        assertTrue(updated.contains("jeap-aws-secretsmanager:my-secret"));
    }

    @Test
    void testIgnoresNonPropertyFiles() throws Exception {
        String content = "aws-secretsmanager:my-secret\n";
        Path file = tempDir.resolve("README.md");
        Files.writeString(file, content);

        new ReplaceTextInSpringProperties(tempDir, OLD_TEXT, NEW_TEXT).execute();

        String updated = Files.readString(file);
        assertEquals(content, updated, "README.md should not be modified");
    }

    @Test
    void testCustomFileNamePattern() throws Exception {
        String content = "aws-secretsmanager:my-secret\n";
        Path file = tempDir.resolve("bootstrap.yml");
        Files.writeString(file, content);

        new ReplaceTextInSpringProperties(tempDir, "bootstrap\\.yml", OLD_TEXT, NEW_TEXT).execute();

        assertTrue(Files.readString(file).contains("jeap-aws-secretsmanager:my-secret"));
    }

    @Test
    void testNoChangeWhenTextNotPresent() throws Exception {
        String content = "spring:\n  application:\n    name: my-app\n";
        Path file = tempDir.resolve("application.yml");
        Files.writeString(file, content);

        new ReplaceTextInSpringProperties(tempDir, OLD_TEXT, NEW_TEXT).execute();

        String updated = Files.readString(file);
        assertEquals(content, updated, "File should be unchanged when prefix is not present");
    }

    @Test
    void testNoErrorWhenDirectoryDoesNotExist() {
        Path nonExistent = tempDir.resolve("non-existent");
        assertDoesNotThrow(() -> new ReplaceTextInSpringProperties(nonExistent, OLD_TEXT, NEW_TEXT).execute());
    }

    @Test
    void testStepName() {
        ReplaceTextInSpringProperties step = new ReplaceTextInSpringProperties(tempDir, OLD_TEXT, NEW_TEXT);
        assertEquals("Replace Text In Spring Properties", step.name());
    }
}



