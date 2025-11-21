package ch.admin.bit.jeap.cli.migration.step.dockerfile;

import ch.admin.bit.jeap.cli.migration.step.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class UpdateDockerfileJavaVersionTest {

    @TempDir
    Path tempDir;

    @Test
    void testUpdateSimpleDockerfile() throws Exception {
        // Given a Dockerfile with Java 21 base image
        String dockerfileContent = """
                FROM eclipse-temurin:21
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then the Java version should be updated to 25-jre-ubi9-minimal
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Java version should be updated to 25-jre-ubi9-minimal");
        assertFalse(updatedContent.contains("FROM eclipse-temurin:21"),
                "Java 21 should be replaced");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfilePath));
    }

    private UpdateDockerfileJavaVersion createUpdateDockerfileStep() {
        return new UpdateDockerfileJavaVersion(tempDir, "eclipse-temurin", "25-jre-ubi9-minimal");
    }

    private UpdateDockerfileJavaVersion createUpdateDockerfileStep(String imageName, String imageTag) {
        return new UpdateDockerfileJavaVersion(tempDir, imageName, imageTag);
    }

    @Test
    void testUpdateDockerfileWithJdkTag() throws Exception {
        // Given a Dockerfile with Java 21 JDK image
        String dockerfileContent = """
                FROM eclipse-temurin:21-jdk
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile (javaVersion is the complete tag)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then the tag should be replaced with the javaVersion value
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Tag should be replaced with 25-jre-ubi9-minimal");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfilePath));
    }

    @Test
    void testUpdateDockerfileWithComplexTag() throws Exception {
        // Given a Dockerfile with Java 21 and complex tag
        String dockerfileContent = """
                FROM eclipse-temurin:21-jdk-alpine
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile (entire tag is replaced)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then the entire tag should be replaced with javaVersion
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Tag should be replaced with 25-jre-ubi9-minimal");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfilePath));
    }

    @Test
    void testUpdateDockerfileWithRegistry() throws Exception {
        // Given a Dockerfile with registry prefix
        String dockerfileContent = """
                FROM registry.example.com/eclipse-temurin:21-jre-ubi9-minimal
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile (entire tag is replaced)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then the tag should be replaced with javaVersion
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM registry.example.com/eclipse-temurin:25-jre-ubi9-minimal"),
                "Tag should be replaced with 25-jre-ubi9-minimal");

        // Verify using helper method
        assertEquals("registry.example.com/eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfilePath));
    }

    @Test
    void testUpdateMultiStageDockerfile() throws Exception {
        // Given a multi-stage Dockerfile
        String dockerfileContent = """
                FROM eclipse-temurin:21-jdk AS builder
                WORKDIR /build
                COPY . .
                RUN ./mvnw package

                FROM eclipse-temurin:21-jre
                WORKDIR /app
                COPY --from=builder /build/target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile (entire tag is replaced)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then both tags should be replaced with javaVersion
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal AS builder"),
                "First stage tag should be replaced with 25-jre-ubi9-minimal");
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal\n"),
                "Second stage tag should be replaced with 25-jre-ubi9-minimal");
        assertFalse(updatedContent.contains(":21-jdk"),
                "Java 21 JDK tag should be replaced");
        assertFalse(updatedContent.contains(":21-jre"),
                "Java 21 JRE tag should be replaced");
    }

    @Test
    void testUpdateMultipleDockerfiles() throws Exception {
        // Given multiple Dockerfile* files
        String dockerfileContent = """
                FROM eclipse-temurin:21
                WORKDIR /app
                """;

        String dockerfileDevContent = """
                FROM eclipse-temurin:21-jdk
                WORKDIR /app
                """;

        String dockerfileProdContent = """
                FROM eclipse-temurin:21-jre
                WORKDIR /app
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Path dockerfileDevPath = tempDir.resolve("Dockerfile.dev");
        Path dockerfileProdPath = tempDir.resolve("Dockerfile.prod");
        Files.writeString(dockerfilePath, dockerfileContent);
        Files.writeString(dockerfileDevPath, dockerfileDevContent);
        Files.writeString(dockerfileProdPath, dockerfileProdContent);

        // When updating the Dockerfiles (all tags replaced with javaVersion)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then all Dockerfiles should have tags replaced with javaVersion
        String updatedContent1 = Files.readString(dockerfilePath);
        assertTrue(updatedContent1.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Dockerfile should be updated");

        String updatedContent2 = Files.readString(dockerfileDevPath);
        assertTrue(updatedContent2.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Dockerfile.dev should be updated");

        String updatedContent3 = Files.readString(dockerfileProdPath);
        assertTrue(updatedContent3.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Dockerfile.prod should be updated");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfilePath));
        assertEquals("eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfileDevPath));
        assertEquals("eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfileProdPath));
    }

    @Test
    void testUpdateFromJava17() throws Exception {
        // Given a Dockerfile with Java 17
        String dockerfileContent = """
                FROM eclipse-temurin:17-jdk
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile (entire tag replaced)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then the tag should be replaced with javaVersion
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Tag should be replaced with 25-jre-ubi9-minimal");
        assertFalse(updatedContent.contains("FROM eclipse-temurin:17-jdk"),
                "Java 17 tag should be replaced");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfilePath));
    }

    @Test
    void testUpdateFromJava11() throws Exception {
        // Given a Dockerfile with Java 11
        String dockerfileContent = """
                FROM eclipse-temurin:11-jre
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile (entire tag replaced)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then the tag should be replaced with javaVersion
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Tag should be replaced with 25-jre-ubi9-minimal");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfilePath));
    }

    @Test
    void testUpdateFromJava8() throws Exception {
        // Given a Dockerfile with Java 8
        String dockerfileContent = """
                FROM eclipse-temurin:8-jdk-alpine
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile (entire tag replaced)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then the tag should be replaced with javaVersion
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Tag should be replaced with 25-jre-ubi9-minimal");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jre-ubi9-minimal", getFromImage(dockerfilePath));
    }

    @Test
    void testUpdateJeapRuntimeCoretto() throws Exception {
        // Given a Dockerfile with jeap-runtime-coretto
        String dockerfileContent = """
                FROM jeap-runtime-coretto:21
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile for jeap-runtime-coretto
        Step updateDockerfile = createUpdateDockerfileStep("jeap-runtime-coretto", "25-jre-ubi9-minimal");
        updateDockerfile.execute();

        // Then the Java version should be updated to 25-jre-ubi9-minimal
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM jeap-runtime-coretto:25-jre-ubi9-minimal"),
                "jeap-runtime-coretto:21 should be updated to 25-jre-ubi9-minimal");

        // Verify using helper method
        assertEquals("jeap-runtime-coretto:25-jre-ubi9-minimal", getFromImage(dockerfilePath));
    }

    @Test
    void testUpdateJeapRuntimeCorettoWithRegistry() throws Exception {
        // Given a Dockerfile with jeap-runtime-coretto from registry
        String dockerfileContent = """
                FROM ecr.amazonaws.com/jeap-runtime-coretto:21.2025-jre-ubi9-minimal0910131842
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile for jeap-runtime-coretto (entire tag replaced)
        Step updateDockerfile = createUpdateDockerfileStep("jeap-runtime-coretto", "25-jre-ubi9-minimal");
        updateDockerfile.execute();

        // Then the tag should be replaced with imageTag
        String updatedContent = Files.readString(dockerfilePath);
        assertThat(updatedContent)
                .contains("FROM ecr.amazonaws.com/jeap-runtime-coretto:25-jre-ubi9-minimal");
    }

    @Test
    void testUpdateMixedImages() throws Exception {
        // Given a Dockerfile with both eclipse-temurin and jeap-runtime-coretto
        String dockerfileContent = """
                FROM eclipse-temurin:21-jdk AS builder
                WORKDIR /build
                COPY . .
                RUN ./mvnw package

                FROM ghcr.io/jeap-admin-ch/jeap-runtime-coretto:17-jre
                WORKDIR /app
                COPY --from=builder /build/target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile for both image types
        Step updateEclipseTemurin = createUpdateDockerfileStep("eclipse-temurin", "25-jre-ubi9-minimal");
        updateEclipseTemurin.execute();
        Step updateJeapRuntime = createUpdateDockerfileStep("jeap-runtime-coretto", "25-jre-ubi9-minimal");
        updateJeapRuntime.execute();

        // Then both tags should be replaced with imageTag
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal AS builder"),
                "eclipse-temurin tag should be replaced with 25-jre-ubi9-minimal");
        assertTrue(updatedContent.contains("FROM ghcr.io/jeap-admin-ch/jeap-runtime-coretto:25-jre-ubi9-minimal"),
                "jeap-runtime-coretto tag should be replaced with 25-jre-ubi9-minimal");
        assertFalse(updatedContent.contains(":21-jdk"),
                "Java 21 JDK tag should be replaced");
        assertFalse(updatedContent.contains(":17-jre"),
                "Java 17 JRE tag should be replaced");
    }

    @Test
    void testNoChangeWhenDifferentImage() throws Exception {
        // Given a Dockerfile with a different base image
        String dockerfileContent = """
                FROM openjdk:21-jdk
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);
        String originalContent = dockerfileContent;

        // When updating the Dockerfile
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then the content should remain unchanged
        String updatedContent = Files.readString(dockerfilePath);
        assertEquals(originalContent, updatedContent,
                "Dockerfile with different base image should not be modified");
    }

    @Test
    void testNoErrorWhenNoDockerfilesFound() throws Exception {
        // Given a directory without any Dockerfile
        // (tempDir is empty)

        // When updating the Dockerfiles
        Step updateDockerfile = createUpdateDockerfileStep();

        // Then no error should occur
        assertDoesNotThrow(() -> updateDockerfile.execute(),
                "Should not throw when no Dockerfiles are found");
    }

    @Test
    void testPreservesFileStructure() throws Exception {
        // Given a Dockerfile with various instructions
        String dockerfileContent = """
                # Build stage
                FROM eclipse-temurin:21-jdk AS builder
                WORKDIR /build
                COPY pom.xml .
                COPY src ./src
                RUN ./mvnw clean package -DskipTests

                # Runtime stage
                FROM eclipse-temurin:21-jre
                WORKDIR /app
                COPY --from=builder /build/target/*.jar app.jar
                EXPOSE 8080
                ENV JAVA_OPTS="-Xmx512m"
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile (entire tags replaced)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then the structure should be preserved and tags replaced
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("# Build stage"));
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal AS builder"));
        assertTrue(updatedContent.contains("# Runtime stage"));
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal\n"));
        assertTrue(updatedContent.contains("WORKDIR /build"));
        assertTrue(updatedContent.contains("COPY pom.xml ."));
        assertTrue(updatedContent.contains("RUN ./mvnw clean package -DskipTests"));
        assertTrue(updatedContent.contains("EXPOSE 8080"));
        assertTrue(updatedContent.contains("ENV JAVA_OPTS=\"-Xmx512m\""));
    }

    @Test
    void testStepName() {
        // Given an UpdateDockerfileJavaVersion step
        Step step = createUpdateDockerfileStep();

        // When getting the step name
        String name = step.name();

        // Then it should return the custom name with image name and tag
        assertEquals("Update Dockerfile eclipse-temurin to 25-jre-ubi9-minimal", name,
                "Step name should include image name and tag");
    }

    @Test
    void testIgnoresNonDockerfileFiles() throws Exception {
        // Given a directory with Dockerfile and other files
        String dockerfileContent = """
                FROM eclipse-temurin:21
                WORKDIR /app
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(dockerfilePath, dockerfileContent);
        Files.writeString(pomPath, "<project></project>");

        // When updating the Dockerfiles
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then only Dockerfile should be updated
        String updatedDockerfile = Files.readString(dockerfilePath);
        assertTrue(updatedDockerfile.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "Dockerfile should be updated");
    }

    @Test
    void testUpdatesOnlyBeginningOfTag() throws Exception {
        // Given a Dockerfile where 21 appears in multiple places
        String dockerfileContent = """
                FROM eclipse-temurin:21-jdk
                ENV VERSION=1.2.1
                EXPOSE 8021
                WORKDIR /app
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile (entire tag replaced)
        Step updateDockerfile = createUpdateDockerfileStep();
        updateDockerfile.execute();

        // Then only the FROM tag should be replaced
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal"),
                "FROM tag should be replaced");
        assertTrue(updatedContent.contains("ENV VERSION=1.2.1"),
                "VERSION should not be modified");
        assertTrue(updatedContent.contains("EXPOSE 8021"),
                "EXPOSE should not be modified");
    }

    private String getFromImage(Path dockerfilePath) throws IOException {
        String content = Files.readString(dockerfilePath);
        Pattern pattern = Pattern.compile("FROM\\s+([^\\s]+)");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
