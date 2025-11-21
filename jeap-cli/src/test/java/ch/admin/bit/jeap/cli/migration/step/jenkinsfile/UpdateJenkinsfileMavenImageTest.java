package ch.admin.bit.jeap.cli.migration.step.jenkinsfile;

import ch.admin.bit.jeap.cli.migration.step.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class UpdateJenkinsfileMavenImageTest {

    @TempDir
    Path tempDir;

    private static final Map<String, String> IMAGE_TAG_MAPPING = Map.of(
            "eclipse-temurin", "25",
            "eclipse-temurin-node", "25-node-22",
            "eclipse-temurin-node-extras", "25-node-22-browsers"
    );

    @Test
    void testUpdateMavenImageWithPathPrefix() throws Exception {
        // Given a Jenkinsfile with mavenImage using eclipse-temurin-node:21-node-22
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                        mavenImage: 'foo/eclipse-temurin-node:21-node-22',
                        mavenDockerUser: 'jenkins',
                        branch: [
                                MASTER : [
                                        systemIntegrationTest: false
                                ]
                        ]
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then the mavenImage should be updated to Java 25 version
        String updatedContent = Files.readString(jenkinsfilePath);
        assertTrue(updatedContent.contains("mavenImage: 'foo/eclipse-temurin-node:25-node-22'"),
                "mavenImage should be updated to eclipse-temurin-node:25-node-22");

        // Verify using helper method
        assertEquals("foo/eclipse-temurin-node:25-node-22", getMavenImage(jenkinsfilePath));
    }

    @Test
    void testUpdateMavenImageWithoutPathPrefix() throws Exception {
        // Given a Jenkinsfile with mavenImage without path prefix
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                        mavenImage: 'eclipse-temurin:21',
                        mavenDockerUser: 'jenkins'
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then the mavenImage should be updated to Java 25
        String updatedContent = Files.readString(jenkinsfilePath);
        assertTrue(updatedContent.contains("mavenImage: 'eclipse-temurin:25'"),
                "mavenImage should be updated to eclipse-temurin:25");

        // Verify using helper method
        assertEquals("eclipse-temurin:25", getMavenImage(jenkinsfilePath));
    }

    @Test
    void testUpdateEclipseTemurinNodeExtras() throws Exception {
        // Given a Jenkinsfile with eclipse-temurin-node-extras image
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                        mavenImage: 'registry.example.com/eclipse-temurin-node-extras:21-node-20-browsers',
                        mavenDockerUser: 'jenkins'
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then the mavenImage should be updated to Java 25 with node 22 and browsers
        String updatedContent = Files.readString(jenkinsfilePath);
        assertTrue(updatedContent.contains("mavenImage: 'registry.example.com/eclipse-temurin-node-extras:25-node-22-browsers'"),
                "mavenImage should be updated to eclipse-temurin-node-extras:25-node-22-browsers");

        // Verify using helper method
        assertEquals("registry.example.com/eclipse-temurin-node-extras:25-node-22-browsers",
                getMavenImage(jenkinsfilePath));
    }

    @Test
    void testUpdateMultipleJenkinsfiles() throws Exception {
        // Given multiple Jenkinsfile* files with different images
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                    mavenImage: 'foo/eclipse-temurin:21'
                )
                """;

        String jenkinsfileTestContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                    mavenImage: 'foo/eclipse-temurin-node:21-node-20'
                )
                """;

        String jenkinsfileProdContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                    mavenImage: 'foo/eclipse-temurin-node-extras:21-node-20-browsers'
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Path jenkinsfileTestPath = tempDir.resolve("Jenkinsfile.test");
        Path jenkinsfileProdPath = tempDir.resolve("Jenkinsfile.prod");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);
        Files.writeString(jenkinsfileTestPath, jenkinsfileTestContent);
        Files.writeString(jenkinsfileProdPath, jenkinsfileProdContent);

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then all Jenkinsfiles should be updated
        String updatedContent1 = Files.readString(jenkinsfilePath);
        assertTrue(updatedContent1.contains("mavenImage: 'foo/eclipse-temurin:25'"),
                "Jenkinsfile should be updated");

        String updatedContent2 = Files.readString(jenkinsfileTestPath);
        assertTrue(updatedContent2.contains("mavenImage: 'foo/eclipse-temurin-node:25-node-22'"),
                "Jenkinsfile.test should be updated");

        String updatedContent3 = Files.readString(jenkinsfileProdPath);
        assertTrue(updatedContent3.contains("mavenImage: 'foo/eclipse-temurin-node-extras:25-node-22-browsers'"),
                "Jenkinsfile.prod should be updated");

        // Verify using helper method
        assertEquals("foo/eclipse-temurin:25", getMavenImage(jenkinsfilePath));
        assertEquals("foo/eclipse-temurin-node:25-node-22", getMavenImage(jenkinsfileTestPath));
        assertEquals("foo/eclipse-temurin-node-extras:25-node-22-browsers", getMavenImage(jenkinsfileProdPath));
    }

    @Test
    void testUpdateMavenImageWithWhitespace() throws Exception {
        // Given a Jenkinsfile with extra whitespace around mavenImage
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                        mavenImage:    'foo/eclipse-temurin-node:21-node-20'   ,
                        mavenDockerUser: 'jenkins'
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then the mavenImage should be updated correctly
        String updatedContent = Files.readString(jenkinsfilePath);
        assertTrue(updatedContent.contains("eclipse-temurin-node:25-node-22"),
                "mavenImage should be updated despite extra whitespace");
    }

    @Test
    void testUpdateMavenImageWithDoubleQuotes() throws Exception {
        // Given a Jenkinsfile with double quotes
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                        mavenImage: "foo/eclipse-temurin:21",
                        mavenDockerUser: "jenkins"
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then the mavenImage should be updated with double quotes preserved
        String updatedContent = Files.readString(jenkinsfilePath);
        assertTrue(updatedContent.contains("mavenImage: \"foo/eclipse-temurin:25\""),
                "mavenImage should be updated with double quotes preserved");
    }

    @Test
    void testUpdateMavenImageValueOnDifferentLine() throws Exception {
        // Given a Jenkinsfile with mavenImage value on a different line
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                        mavenImage:
                            'foo/eclipse-temurin-node:21-node-20',
                        mavenDockerUser: 'jenkins'
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then the mavenImage should be updated correctly
        String updatedContent = Files.readString(jenkinsfilePath);
        assertTrue(updatedContent.contains("eclipse-temurin-node:25-node-22"),
                "mavenImage should be updated even when value is on different line");
    }

    @Test
    void testNoChangeWhenMavenImageNotPresent() throws Exception {
        // Given a Jenkinsfile without mavenImage
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                        mavenDockerUser: 'jenkins',
                        branch: [
                                MASTER : [
                                        systemIntegrationTest: false
                                ]
                        ]
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then the content should remain unchanged
        String updatedContent = Files.readString(jenkinsfilePath);
        assertEquals(jenkinsfileContent, updatedContent,
                "Content should remain unchanged when mavenImage is not present");
    }

    @Test
    void testNoChangeWhenImageNotRecognized() throws Exception {
        // Given a Jenkinsfile with an unrecognized image
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                        mavenImage: 'foo/some-other-image:21',
                        mavenDockerUser: 'jenkins'
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);
        String originalContent = jenkinsfileContent;

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then the content should remain unchanged
        String updatedContent = Files.readString(jenkinsfilePath);
        assertEquals(originalContent, updatedContent,
                "Content should remain unchanged when image is not recognized");
    }

    @Test
    void testNoErrorWhenNoJenkinsfilesFound() throws Exception {
        // Given a directory without any Jenkinsfile
        // (tempDir is empty)

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);

        // Then no error should occur
        assertDoesNotThrow(() -> updateMavenImage.execute(),
                "Should not throw when no Jenkinsfiles are found");
    }

    @Test
    void testNoErrorWhenDirectoryDoesNotExist() throws Exception {
        // Given a non-existent directory path
        Path nonExistentDir = tempDir.resolve("non-existent");

        Step updateMavenImage = new UpdateJenkinsfileMavenImage(nonExistentDir, IMAGE_TAG_MAPPING);

        // Then no error should occur
        assertDoesNotThrow(() -> updateMavenImage.execute(),
                "Should not throw when directory does not exist");
    }

    @Test
    void testPreservesFileStructure() throws Exception {
        // Given a Jenkinsfile with complex structure
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                def enforceOpenSourcePreconditions = { context ->
                    ch.admin.bit.jeap.microservicePipeline.oss.OpenSourcePreconditionEnforcer.enforcePreconditions(context)
                }
                
                jeapBuildPipeline(
                        afterSetup: enforceOpenSourcePreconditions,
                        mavenImage: 'foo/eclipse-temurin-node:21-node-22',
                        mavenDockerUser: 'jenkins',
                        branch: [
                                MASTER : [
                                        systemIntegrationTest: false,
                                        deployStage          : null,
                                        nextStage            : null,
                                        additionalMavenArgs  : '-P maven-central-publish'
                                ],
                                FEATURE: [
                                        integrationTest  : true,
                                        publish          : true
                               ]
                        ]
                )
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then the structure should be preserved
        String updatedContent = Files.readString(jenkinsfilePath);
        assertTrue(updatedContent.contains("mavenImage: 'foo/eclipse-temurin-node:25-node-22'"));
        assertTrue(updatedContent.contains("enforceOpenSourcePreconditions"));
        assertTrue(updatedContent.contains("afterSetup: enforceOpenSourcePreconditions"));
        assertTrue(updatedContent.contains("mavenDockerUser: 'jenkins'"));
        assertTrue(updatedContent.contains("MASTER"));
        assertTrue(updatedContent.contains("FEATURE"));
        assertTrue(updatedContent.contains("additionalMavenArgs"));
    }

    @Test
    void testStepName() {
        // Given an UpdateJenkinsfileMavenImage step
        Step step = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);

        // When getting the step name
        String name = step.name();

        // Then it should return the custom name
        assertEquals("Update Jenkinsfile Maven Image", name,
                "Step name should be 'Update Jenkinsfile Maven Image'");
    }

    @Test
    void testIgnoresNonJenkinsfileFiles() throws Exception {
        // Given a directory with Jenkinsfile and other files
        String jenkinsfileContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                    mavenImage: 'foo/eclipse-temurin:21'
                )
                """;

        String readmeContent = """
                # README
                This is not a Jenkinsfile
                mavenImage: 'foo/eclipse-temurin:21'
                """;

        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Path readmePath = tempDir.resolve("README.md");
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);
        Files.writeString(readmePath, readmeContent);
        Files.writeString(pomPath, "<project></project>");

        String originalReadme = readmeContent;

        // When updating the mavenImage
        Step updateMavenImage = new UpdateJenkinsfileMavenImage(tempDir, IMAGE_TAG_MAPPING);
        updateMavenImage.execute();

        // Then only Jenkinsfile should be updated
        String updatedJenkinsfile = Files.readString(jenkinsfilePath);
        assertTrue(updatedJenkinsfile.contains("eclipse-temurin:25"),
                "Jenkinsfile should be updated");

        String updatedReadme = Files.readString(readmePath);
        assertEquals(originalReadme, updatedReadme,
                "README.md should not be modified");
    }

    private String getMavenImage(Path jenkinsfilePath) throws IOException {
        String content = Files.readString(jenkinsfilePath);
        Pattern pattern = Pattern.compile("mavenImage:\\s*['\"]([^'\"]+)['\"]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
