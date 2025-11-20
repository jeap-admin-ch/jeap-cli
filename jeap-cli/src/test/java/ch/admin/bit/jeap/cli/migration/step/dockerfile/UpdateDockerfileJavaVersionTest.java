package ch.admin.bit.jeap.cli.migration.step.dockerfile;

import ch.admin.bit.jeap.cli.migration.step.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the Java version should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25"),
                "Java version should be updated to 25");
        assertFalse(updatedContent.contains("FROM eclipse-temurin:21"),
                "Java 21 should be replaced");

        // Verify using helper method
        assertEquals("eclipse-temurin:25", getFromImage(dockerfilePath));
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the Java version should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jdk"),
                "Java version should be updated to 25-jdk");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jdk", getFromImage(dockerfilePath));
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the Java version should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jdk-alpine"),
                "Java version should be updated to 25-jdk-alpine");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jdk-alpine", getFromImage(dockerfilePath));
    }

    @Test
    void testUpdateDockerfileWithRegistry() throws Exception {
        // Given a Dockerfile with registry prefix
        String dockerfileContent = """
                FROM registry.example.com/eclipse-temurin:21-jdk
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the Java version should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM registry.example.com/eclipse-temurin:25-jdk"),
                "Java version should be updated to 25-jdk");

        // Verify using helper method
        assertEquals("registry.example.com/eclipse-temurin:25-jdk", getFromImage(dockerfilePath));
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then both Java versions should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jdk AS builder"),
                "First stage should be updated to 25-jdk");
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre"),
                "Second stage should be updated to 25-jre");
        assertFalse(updatedContent.contains(":21-jdk"),
                "Java 21 JDK should be replaced");
        assertFalse(updatedContent.contains(":21-jre"),
                "Java 21 JRE should be replaced");
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

        // When updating the Dockerfiles
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then all Dockerfiles should be updated
        String updatedContent1 = Files.readString(dockerfilePath);
        assertTrue(updatedContent1.contains("FROM eclipse-temurin:25"),
                "Dockerfile should be updated");

        String updatedContent2 = Files.readString(dockerfileDevPath);
        assertTrue(updatedContent2.contains("FROM eclipse-temurin:25-jdk"),
                "Dockerfile.dev should be updated");

        String updatedContent3 = Files.readString(dockerfileProdPath);
        assertTrue(updatedContent3.contains("FROM eclipse-temurin:25-jre"),
                "Dockerfile.prod should be updated");

        // Verify using helper method
        assertEquals("eclipse-temurin:25", getFromImage(dockerfilePath));
        assertEquals("eclipse-temurin:25-jdk", getFromImage(dockerfileDevPath));
        assertEquals("eclipse-temurin:25-jre", getFromImage(dockerfileProdPath));
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the Java version should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jdk"),
                "Java 17 should be updated to 25-jdk");
        assertFalse(updatedContent.contains("FROM eclipse-temurin:17-jdk"),
                "Java 17 should be replaced");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jdk", getFromImage(dockerfilePath));
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the Java version should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre"),
                "Java 11 should be updated to 25-jre");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jre", getFromImage(dockerfilePath));
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the Java version should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jdk-alpine"),
                "Java 8 should be updated to 25-jdk-alpine");

        // Verify using helper method
        assertEquals("eclipse-temurin:25-jdk-alpine", getFromImage(dockerfilePath));
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the Java version should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM jeap-runtime-coretto:25"),
                "jeap-runtime-coretto:21 should be updated to 25");

        // Verify using helper method
        assertEquals("jeap-runtime-coretto:25", getFromImage(dockerfilePath));
    }

    @Test
    void testUpdateJeapRuntimeCorettoWithRegistry() throws Exception {
        // Given a Dockerfile with jeap-runtime-coretto from registry
        String dockerfileContent = """
                FROM ghcr.io/jeap-admin-ch/jeap-runtime-coretto:21-jdk
                WORKDIR /app
                COPY target/*.jar app.jar
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the Java version should be updated to 25
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM ghcr.io/jeap-admin-ch/jeap-runtime-coretto:25-jdk"),
                "jeap-runtime-coretto with registry should be updated to 25-jdk");

        // Verify using helper method
        assertEquals("ghcr.io/jeap-admin-ch/jeap-runtime-coretto:25-jdk", getFromImage(dockerfilePath));
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then both images should be updated
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jdk AS builder"),
                "eclipse-temurin should be updated to 25-jdk");
        assertTrue(updatedContent.contains("FROM ghcr.io/jeap-admin-ch/jeap-runtime-coretto:25-jre"),
                "jeap-runtime-coretto should be updated to 25-jre");
        assertFalse(updatedContent.contains(":21-jdk"),
                "Java 21 JDK should be replaced");
        assertFalse(updatedContent.contains(":17-jre"),
                "Java 17 JRE should be replaced");
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
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
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
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);

        // Then no error should occur
        assertDoesNotThrow(() -> updateDockerfile.execute(),
                "Should not throw when no Dockerfiles are found");
    }

    @Test
    void testNoErrorWhenDirectoryDoesNotExist() throws Exception {
        // Given a non-existent directory path
        Path nonExistentDir = tempDir.resolve("non-existent");

        Step updateDockerfile = new UpdateDockerfileJavaVersion(nonExistentDir);

        // Then no error should occur
        assertDoesNotThrow(() -> updateDockerfile.execute(),
                "Should not throw when directory does not exist");
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then the structure should be preserved
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("# Build stage"));
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jdk AS builder"));
        assertTrue(updatedContent.contains("# Runtime stage"));
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jre"));
        assertTrue(updatedContent.contains("WORKDIR /build"));
        assertTrue(updatedContent.contains("COPY pom.xml ."));
        assertTrue(updatedContent.contains("RUN ./mvnw clean package -DskipTests"));
        assertTrue(updatedContent.contains("EXPOSE 8080"));
        assertTrue(updatedContent.contains("ENV JAVA_OPTS=\"-Xmx512m\""));
    }

    @Test
    void testStepName() {
        // Given an UpdateDockerfileJavaVersion step
        Step step = new UpdateDockerfileJavaVersion(tempDir);

        // When getting the step name
        String name = step.name();

        // Then it should return the custom name
        assertEquals("Update Dockerfile Java Version", name,
                "Step name should be 'Update Dockerfile Java Version'");
    }

    @Test
    void testIgnoresNonDockerfileFiles() throws Exception {
        // Given a directory with Dockerfile and other files
        String dockerfileContent = """
                FROM eclipse-temurin:21
                WORKDIR /app
                """;

        String readmeContent = """
                # README
                FROM eclipse-temurin:21
                This is not a Dockerfile
                """;

        Path dockerfilePath = tempDir.resolve("Dockerfile");
        Path readmePath = tempDir.resolve("README.md");
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(dockerfilePath, dockerfileContent);
        Files.writeString(readmePath, readmeContent);
        Files.writeString(pomPath, "<project></project>");

        String originalReadme = readmeContent;

        // When updating the Dockerfiles
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then only Dockerfile should be updated
        String updatedDockerfile = Files.readString(dockerfilePath);
        assertTrue(updatedDockerfile.contains("FROM eclipse-temurin:25"),
                "Dockerfile should be updated");

        String updatedReadme = Files.readString(readmePath);
        assertEquals(originalReadme, updatedReadme,
                "README.md should not be modified");
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

        // When updating the Dockerfile
        Step updateDockerfile = new UpdateDockerfileJavaVersion(tempDir);
        updateDockerfile.execute();

        // Then only the FROM statement should be updated
        String updatedContent = Files.readString(dockerfilePath);
        assertTrue(updatedContent.contains("FROM eclipse-temurin:25-jdk"),
                "FROM statement should be updated");
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
