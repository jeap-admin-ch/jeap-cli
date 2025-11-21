package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class UpdateJibBaseImageTest {

    @TempDir
    Path tempDir;

    @Test
    void testUpdateSimpleJibConfiguration() throws Exception {
        // Given a pom.xml with jib-maven-plugin using amazoncorretto:21
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>test</artifactId>
                    <version>1.0.0</version>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.google.cloud.tools</groupId>
                                <artifactId>jib-maven-plugin</artifactId>
                                <configuration>
                                    <from>
                                        <image>amazoncorretto:21</image>
                                    </from>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When updating jib base image
        Step updateJib = createUpdateJibBaseImageStep();
        updateJib.execute();

        // Then the image should be updated to Java 25
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<image>amazoncorretto:25-al2032-headless</image>"),
                "Jib base image should be updated to 25-al2032-headless");
        assertFalse(updatedContent.contains("<image>amazoncorretto:21</image>"),
                "Old image tag should be replaced");

        // Verify using helper method
        assertEquals("amazoncorretto:25-al2032-headless", getJibFromImage(updatedContent));
    }

    private UpdateJibBaseImage createUpdateJibBaseImageStep() {
        return new UpdateJibBaseImage(tempDir, "amazoncorretto", "25-al2032-headless");
    }

    @Test
    void testUpdateJibConfigurationWithRegistry() throws Exception {
        // Given a pom.xml with registry prefix
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>test</artifactId>
                
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

        // When updating jib base image
        Step updateJib = createUpdateJibBaseImageStep();
        updateJib.execute();

        // Then the image should be updated
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<image>host:1234/amazoncorretto:25-al2032-headless</image>"),
                "Jib base image with registry should be updated");

        // Verify using helper method
        assertEquals("host:1234/amazoncorretto:25-al2032-headless", getJibFromImage(updatedContent));
    }

    @Test
    void testUpdateJibConfigurationWithMultiplePaths() throws Exception {
        // Given a pom.xml with multiple path segments
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>test</artifactId>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.google.cloud.tools</groupId>
                                <artifactId>jib-maven-plugin</artifactId>
                                <configuration>
                                    <from>
                                        <image>registry.example.com/path/to/amazoncorretto:17</image>
                                    </from>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When updating jib base image
        Step updateJib = createUpdateJibBaseImageStep();
        updateJib.execute();

        // Then the image should be updated
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<image>registry.example.com/path/to/amazoncorretto:25-al2032-headless</image>"),
                "Jib base image with multiple paths should be updated");

        // Verify using helper method
        assertEquals("registry.example.com/path/to/amazoncorretto:25-al2032-headless", getJibFromImage(updatedContent));
    }

    @Test
    void testUpdateMultiplePomFiles() throws Exception {
        // Given multiple pom.xml files in different directories
        Files.createDirectories(tempDir.resolve("module1"));
        Files.createDirectories(tempDir.resolve("module2"));

        String pomContent1 = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>module1</artifactId>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.google.cloud.tools</groupId>
                                <artifactId>jib-maven-plugin</artifactId>
                                <configuration>
                                    <from>
                                        <image>amazoncorretto:21</image>
                                    </from>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        String pomContent2 = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>module2</artifactId>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.google.cloud.tools</groupId>
                                <artifactId>jib-maven-plugin</artifactId>
                                <configuration>
                                    <from>
                                        <image>repo.example.com/amazoncorretto:17</image>
                                    </from>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        Path pom1Path = tempDir.resolve("module1/pom.xml");
        Path pom2Path = tempDir.resolve("module2/pom.xml");
        Files.writeString(pom1Path, pomContent1);
        Files.writeString(pom2Path, pomContent2);

        // When updating jib base image
        Step updateJib = createUpdateJibBaseImageStep();
        updateJib.execute();

        // Then both pom files should be updated
        String updatedContent1 = Files.readString(pom1Path);
        assertTrue(updatedContent1.contains("<image>amazoncorretto:25-al2032-headless</image>"),
                "First pom should be updated");

        String updatedContent2 = Files.readString(pom2Path);
        assertTrue(updatedContent2.contains("<image>repo.example.com/amazoncorretto:25-al2032-headless</image>"),
                "Second pom should be updated");

        // Verify using helper method
        assertEquals("amazoncorretto:25-al2032-headless", getJibFromImage(updatedContent1));
        assertEquals("repo.example.com/amazoncorretto:25-al2032-headless", getJibFromImage(updatedContent2));
    }

    @Test
    void testNoChangeWhenNoJibPlugin() throws Exception {
        // Given a pom.xml without jib-maven-plugin
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>test</artifactId>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);
        String originalContent = pomContent;

        // When updating jib base image
        Step updateJib = createUpdateJibBaseImageStep();
        updateJib.execute();

        // Then the content should remain unchanged
        String updatedContent = Files.readString(pomPath);
        assertEquals(originalContent, updatedContent,
                "Pom without jib plugin should not be modified");
    }

    @Test
    void testNoChangeWhenDifferentBaseImage() throws Exception {
        // Given a pom.xml with jib but different base image
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>test</artifactId>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.google.cloud.tools</groupId>
                                <artifactId>jib-maven-plugin</artifactId>
                                <configuration>
                                    <from>
                                        <image>eclipse-temurin:21</image>
                                    </from>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);
        String originalContent = pomContent;

        // When updating jib base image
        Step updateJib = createUpdateJibBaseImageStep();
        updateJib.execute();

        // Then the content should remain unchanged
        String updatedContent = Files.readString(pomPath);
        assertEquals(originalContent, updatedContent,
                "Pom with different base image should not be modified");
    }

    @Test
    void testNoErrorWhenNoPomFiles() throws Exception {
        // Given a directory without any pom.xml files
        // (tempDir is empty)

        // When updating jib base image
        Step updateJib = createUpdateJibBaseImageStep();

        // Then no error should occur
        assertDoesNotThrow(() -> updateJib.execute(),
                "Should not throw when no pom files are found");
    }

    @Test
    void testNoErrorWhenDirectoryDoesNotExist() throws Exception {
        // Given a non-existent directory
        Path nonExistentDir = tempDir.resolve("non-existent");

        Step updateJib = new UpdateJibBaseImage(nonExistentDir, "amazoncorretto", "25-al2032-headless");

        // Then no error should occur
        assertDoesNotThrow(() -> updateJib.execute(),
                "Should not throw when directory does not exist");
    }

    @Test
    void testPreservesFileStructure() throws Exception {
        // Given a pom.xml with various elements
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>test</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <java.version>25</java.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.google.cloud.tools</groupId>
                                <artifactId>jib-maven-plugin</artifactId>
                                <configuration>
                                    <container>
                                        <mainClass>com.example.Main</mainClass>
                                    </container>
                                    <from>
                                        <image>host:1234/amazoncorretto:21-al2023-headless</image>
                                    </from>
                                    <to>
                                        <image>example.com/${project.artifactId}:${project.version}</image>
                                    </to>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When updating jib base image
        Step updateJib = createUpdateJibBaseImageStep();
        updateJib.execute();

        // Then the structure should be preserved
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<java.version>25</java.version>"));
        assertTrue(updatedContent.contains("<mainClass>com.example.Main</mainClass>"));
        assertTrue(updatedContent.contains("<to>"));
        assertTrue(updatedContent.contains("<dependencies>"));
        assertTrue(updatedContent.contains("<image>host:1234/amazoncorretto:25-al2032-headless</image>"));
    }

    @Test
    void testStepName() {
        // Given an UpdateJibBaseImage step
        Step step = createUpdateJibBaseImageStep();

        // When getting the step name
        String name = step.name();

        // Then it should return the custom name with image name and tag
        assertEquals("Update Jib Base Image to amazoncorretto:25-al2032-headless", name,
                "Step name should include image name and tag");
    }

    @Test
    void testUpdateFromJava17() throws Exception {
        // Given a pom.xml with Java 17 amazoncorretto
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>test</artifactId>
                
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.google.cloud.tools</groupId>
                                <artifactId>jib-maven-plugin</artifactId>
                                <configuration>
                                    <from>
                                        <image>amazoncorretto:17-al2023</image>
                                    </from>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When updating jib base image
        Step updateJib = createUpdateJibBaseImageStep();
        updateJib.execute();

        // Then the image should be updated to Java 25
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<image>amazoncorretto:25-al2032-headless</image>"),
                "Java 17 should be updated to 25-al2032-headless");

        // Verify using helper method
        assertEquals("amazoncorretto:25-al2032-headless", getJibFromImage(updatedContent));
    }

    private String getJibFromImage(String pomContent) {
        Pattern pattern = Pattern.compile("<from>\\s*<image>([^<]+)</image>\\s*</from>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(pomContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
