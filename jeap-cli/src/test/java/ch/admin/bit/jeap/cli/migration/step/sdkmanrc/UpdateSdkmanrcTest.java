package ch.admin.bit.jeap.cli.migration.step.sdkmanrc;

import ch.admin.bit.jeap.cli.migration.step.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UpdateSdkmanrcTest {

    @TempDir
    Path tempDir;

    @Test
    void testUpdateSdkmanrcWithDistribution() throws Exception {
        // Given a .sdkmanrc file with Java 21 and Temurin distribution
        String sdkmanrcContent = """
                # Enable auto-env through the sdkman_auto_env config
                # Add key=value pairs of SDKs to use below
                java=21.0.2-tem
                """;

        Path sdkmanrcPath = tempDir.resolve(".sdkmanrc");
        Files.writeString(sdkmanrcPath, sdkmanrcContent);

        // When updating the .sdkmanrc
        Step updateSdkmanrc = new UpdateSdkmanrc(tempDir, "25");
        updateSdkmanrc.execute();

        // Then the Java version should be updated to 25 with distribution preserved
        String updatedContent = Files.readString(sdkmanrcPath);
        assertTrue(updatedContent.contains("java=25-tem"),
                "Java version should be updated to 25-tem");
        assertFalse(updatedContent.contains("java=21.0.2-tem"),
                "Java 21.0.2 should be replaced");
    }

    @Test
    void testUpdateSdkmanrcWithDifferentDistribution() throws Exception {
        // Given a .sdkmanrc file with Microsoft distribution
        String sdkmanrcContent = """
                java=17.0.1-ms
                """;

        Path sdkmanrcPath = tempDir.resolve(".sdkmanrc");
        Files.writeString(sdkmanrcPath, sdkmanrcContent);

        // When updating the .sdkmanrc
        Step updateSdkmanrc = new UpdateSdkmanrc(tempDir, "25");
        updateSdkmanrc.execute();

        // Then the Java version should be updated with distribution preserved
        String updatedContent = Files.readString(sdkmanrcPath);
        assertTrue(updatedContent.contains("java=25-ms"),
                "Java version should be updated to 25-ms");
    }

    @Test
    void testUpdateSdkmanrcWithoutDistribution() throws Exception {
        // Given a .sdkmanrc file without distribution suffix
        String sdkmanrcContent = """
                java=21
                """;

        Path sdkmanrcPath = tempDir.resolve(".sdkmanrc");
        Files.writeString(sdkmanrcPath, sdkmanrcContent);

        // When updating the .sdkmanrc
        Step updateSdkmanrc = new UpdateSdkmanrc(tempDir, "25");
        updateSdkmanrc.execute();

        // Then the Java version should be updated
        String updatedContent = Files.readString(sdkmanrcPath);
        assertTrue(updatedContent.contains("java=25"),
                "Java version should be updated to 25");
        assertFalse(updatedContent.contains("java=21"),
                "Java 21 should be replaced");
    }

    @Test
    void testUpdateSdkmanrcPreservesComments() throws Exception {
        // Given a .sdkmanrc file with comments
        String sdkmanrcContent = """
                # Enable auto-env through the sdkman_auto_env config
                # Add key=value pairs of SDKs to use below
                java=21.0.2-tem
                # Other comment
                """;

        Path sdkmanrcPath = tempDir.resolve(".sdkmanrc");
        Files.writeString(sdkmanrcPath, sdkmanrcContent);

        // When updating the .sdkmanrc
        Step updateSdkmanrc = new UpdateSdkmanrc(tempDir, "25");
        updateSdkmanrc.execute();

        // Then comments should be preserved
        String updatedContent = Files.readString(sdkmanrcPath);
        assertTrue(updatedContent.contains("# Enable auto-env"),
                "Comments should be preserved");
        assertTrue(updatedContent.contains("# Add key=value"),
                "Comments should be preserved");
        assertTrue(updatedContent.contains("# Other comment"),
                "Comments should be preserved");
        assertTrue(updatedContent.contains("java=25-tem"),
                "Java version should be updated");
    }

    @Test
    void testUpdateSdkmanrcPreservesOtherSdks() throws Exception {
        // Given a .sdkmanrc file with multiple SDKs
        String sdkmanrcContent = """
                java=21.0.2-tem
                maven=3.9.5
                gradle=8.5
                """;

        Path sdkmanrcPath = tempDir.resolve(".sdkmanrc");
        Files.writeString(sdkmanrcPath, sdkmanrcContent);

        // When updating the .sdkmanrc
        Step updateSdkmanrc = new UpdateSdkmanrc(tempDir, "25");
        updateSdkmanrc.execute();

        // Then other SDKs should be preserved
        String updatedContent = Files.readString(sdkmanrcPath);
        assertTrue(updatedContent.contains("java=25-tem"),
                "Java version should be updated");
        assertTrue(updatedContent.contains("maven=3.9.5"),
                "Maven version should be preserved");
        assertTrue(updatedContent.contains("gradle=8.5"),
                "Gradle version should be preserved");
    }

    @Test
    void testUpdateSdkmanrcInSubdirectory() throws Exception {
        // Given a .sdkmanrc file in a subdirectory
        Path subDir = tempDir.resolve("subproject");
        Files.createDirectories(subDir);

        String sdkmanrcContent = "java=21.0.2-tem\n";
        Path sdkmanrcPath = subDir.resolve(".sdkmanrc");
        Files.writeString(sdkmanrcPath, sdkmanrcContent);

        // When updating the .sdkmanrc
        Step updateSdkmanrc = new UpdateSdkmanrc(tempDir, "25");
        updateSdkmanrc.execute();

        // Then the file in subdirectory should be updated
        String updatedContent = Files.readString(sdkmanrcPath);
        assertTrue(updatedContent.contains("java=25-tem"),
                "Java version should be updated in subdirectory");
    }

    @Test
    void testNoErrorWhenNoSdkmanrcFound() throws Exception {
        // Given a directory without any .sdkmanrc file
        // (tempDir is empty)

        // When updating the .sdkmanrc
        Step updateSdkmanrc = new UpdateSdkmanrc(tempDir, "25");

        // Then no error should occur
        assertDoesNotThrow(updateSdkmanrc::execute,
                "Should not throw when no .sdkmanrc files are found");
    }

    @Test
    void testNoChangeWhenAlreadyUpdated() throws Exception {
        // Given a .sdkmanrc file already at Java 25
        String sdkmanrcContent = """
                java=25-tem
                """;

        Path sdkmanrcPath = tempDir.resolve(".sdkmanrc");
        Files.writeString(sdkmanrcPath, sdkmanrcContent);

        // When updating the .sdkmanrc
        Step updateSdkmanrc = new UpdateSdkmanrc(tempDir, "25");
        updateSdkmanrc.execute();

        // Then the file should not be modified
        String updatedContent = Files.readString(sdkmanrcPath);
        assertEquals("java=25-tem\n", updatedContent,
                "Content should remain unchanged");
    }

    @Test
    void testUpdateMultipleSdkmanrcFiles() throws Exception {
        // Given multiple .sdkmanrc files
        String sdkmanrcContent1 = "java=21.0.2-tem\n";
        String sdkmanrcContent2 = "java=17.0.1-ms\n";

        Path sdkmanrcPath1 = tempDir.resolve(".sdkmanrc");
        Path subDir = tempDir.resolve("subproject");
        Files.createDirectories(subDir);
        Path sdkmanrcPath2 = subDir.resolve(".sdkmanrc");

        Files.writeString(sdkmanrcPath1, sdkmanrcContent1);
        Files.writeString(sdkmanrcPath2, sdkmanrcContent2);

        // When updating the .sdkmanrc files
        Step updateSdkmanrc = new UpdateSdkmanrc(tempDir, "25");
        updateSdkmanrc.execute();

        // Then both files should be updated
        String updatedContent1 = Files.readString(sdkmanrcPath1);
        String updatedContent2 = Files.readString(sdkmanrcPath2);

        assertTrue(updatedContent1.contains("java=25-tem"),
                "First .sdkmanrc should be updated");
        assertTrue(updatedContent2.contains("java=25-ms"),
                "Second .sdkmanrc should be updated");
    }
}
