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

import static org.junit.jupiter.api.Assertions.*;

class Java25MigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testSuccessfulMigration() throws Exception {
        // Given a project directory with a pom.xml containing java.version 17 and jib-maven-plugin
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
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.google.cloud.tools</groupId>
                                <artifactId>jib-maven-plugin</artifactId>
                                <configuration>
                                    <from>
                                        <image>host:1234/amazoncorretto:21-al2023-headless</image>
                                    </from>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // And a Jenkinsfile with mavenImage using Java 21
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
                                        publish          : true,
                                        buildNumberGenerator: ch.admin.bit.jeap.microservicePipeline.branching.BuildNumberGenerator.BRANCH_NAME_SNAPSHOT
                               ]
                        ]
                )
                """;
        Path jenkinsfilePath = tempDir.resolve("Jenkinsfile");
        Files.writeString(jenkinsfilePath, jenkinsfileContent);

        // And a Jenkinsfile.test with a different image
        String jenkinsfileTestContent = """
                @Library('jeap-microservice-pipeline@v2') _
                
                jeapBuildPipeline(
                        mavenImage: 'foo/eclipse-temurin:21',
                        mavenDockerUser: 'jenkins'
                )
                """;
        Path jenkinsfileTestPath = tempDir.resolve("Jenkinsfile.test");
        Files.writeString(jenkinsfileTestPath, jenkinsfileTestContent);

        // And a Dockerfile with Java 21 base image
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

        // And a GitHub Actions workflow using jeap-codebuild-java:21
        String workflowContent = """
                name: jeap-maven-build
                
                on:
                  push:
                    branches: [ "**" ]
                
                jobs:
                  build:
                    uses: NIVEL-GITHUB/jeap-github-actions/.github/workflows/jeap-maven-build.yml@v1
                    with:
                      codebuild-image: "jeap-codebuild-java:21-node-22"
                      system-name: "applicationplatform"
                      trigger-deployment: true
                """;
        Path workflowsDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(workflowsDir);
        Path workflowPath = workflowsDir.resolve("build.yml");
        Files.writeString(workflowPath, workflowContent);

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
                        "versions:2.19.1:update-parent",
                        "-Dincludes=ch.admin.bit.jeap",
                        "-DgenerateBackupPoms=false"),
                executedCommand.command(),
                "Should execute Maven with correct parent update arguments");
        assertEquals(tempDir, executedCommand.workingDirectory(),
                "Maven should execute in the project root directory");

        // And the java.version property should be updated to 25
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<java.version>25</java.version>"),
                "java.version should be updated to 25");

        // And the maven.compiler.release property should be updated to 25
        assertTrue(updatedContent.contains("<maven.compiler.release>25</maven.compiler.release>"),
                "maven.compiler.release should be updated to 25");

        // Verify using helper methods
        assertEquals("25", getJavaVersion(pomPath),
                "java.version should be exactly 25");
        assertEquals("25", getMavenCompilerRelease(pomPath),
                "maven.compiler.release should be exactly 25");

        // And the Jenkinsfile mavenImage should be updated to Java 25
        String updatedJenkinsfileContent = Files.readString(jenkinsfilePath);
        assertTrue(updatedJenkinsfileContent.contains("mavenImage: 'foo/eclipse-temurin-node:25-node-22'"),
                "Jenkinsfile mavenImage should be updated to eclipse-temurin-node:25-node-22");

        // Verify using helper method
        assertEquals("foo/eclipse-temurin-node:25-node-22", getMavenImage(jenkinsfilePath),
                "Jenkinsfile mavenImage should be exactly foo/eclipse-temurin-node:25-node-22");

        // And the Jenkinsfile.test mavenImage should be updated to Java 25
        String updatedJenkinsfileTestContent = Files.readString(jenkinsfileTestPath);
        assertTrue(updatedJenkinsfileTestContent.contains("mavenImage: 'foo/eclipse-temurin:25'"),
                "Jenkinsfile.test mavenImage should be updated to eclipse-temurin:25");

        // Verify using helper method
        assertEquals("foo/eclipse-temurin:25", getMavenImage(jenkinsfileTestPath),
                "Jenkinsfile.test mavenImage should be exactly foo/eclipse-temurin:25");

        // And the Dockerfile should be updated to Java 25
        String updatedDockerfileContent = Files.readString(dockerfilePath);
        assertTrue(updatedDockerfileContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal AS builder"),
                "Dockerfile builder stage should be updated to eclipse-temurin:25-jre-ubi9-minimal");
        assertTrue(updatedDockerfileContent.contains("FROM eclipse-temurin:25-jre-ubi9-minimal\n"),
                "Dockerfile runtime stage should be updated to eclipse-temurin:25-jre-ubi9-minimal");
        assertFalse(updatedDockerfileContent.contains(":21-jdk"),
                "Dockerfile should not contain :21-jdk");
        assertFalse(updatedDockerfileContent.contains(":21-jre"),
                "Dockerfile should not contain :21-jre");

        // And the pom.xml jib base image should be updated to Java 25
        assertTrue(updatedContent.contains("<image>host:1234/amazoncorretto:25-al2023-headless</image>"),
                "Jib base image should be updated to 25-al2023-headless");
        assertFalse(updatedContent.contains(":21-al2023-headless"),
                "Old jib base image tag should be replaced");

        // And the GitHub Actions workflow codebuild-image should be updated to Java 25
        String updatedWorkflowContent = Files.readString(workflowPath);
        assertTrue(updatedWorkflowContent.contains("codebuild-image: \"jeap-codebuild-java:25-node-22\""),
                "GitHub Actions codebuild-image should be updated to jeap-codebuild-java:25-node-22");
        assertFalse(updatedWorkflowContent.contains("21-node-22"),
                "Old codebuild-image tag should be replaced");
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

    private String getMavenCompilerRelease(Path pomPath) throws Exception {
        String content = Files.readString(pomPath);
        Pattern pattern = Pattern.compile("<maven\\.compiler\\.release\\s*>([^<]*)</maven\\.compiler\\.release>");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String getMavenImage(Path jenkinsfilePath) throws Exception {
        String content = Files.readString(jenkinsfilePath);
        Pattern pattern = Pattern.compile("mavenImage:\\s*['\"]([^'\"]+)['\"]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
