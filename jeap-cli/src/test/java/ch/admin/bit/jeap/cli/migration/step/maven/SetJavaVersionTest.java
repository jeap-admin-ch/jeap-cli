package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SetJavaVersionTest {

    @TempDir
    Path tempDir;

    @Test
    void testUpdateExistingJavaVersion() throws Exception {
        // Given a pom.xml with java.version property set to 17
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>

                    <properties>
                        <java.version>17</java.version>
                        <maven.compiler.source>17</maven.compiler.source>
                    </properties>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When updating to Java 25
        Step setJavaVersion = new SetJavaVersion(tempDir, "25");
        setJavaVersion.execute();

        // Then the java.version should be updated to 25
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<java.version>25</java.version>"),
                "java.version should be updated to 25");

        // And maven.compiler.release should be added with value 25
        assertTrue(updatedContent.contains("<maven.compiler.release>25</maven.compiler.release>"),
                "maven.compiler.release should be added with value 25");

        // And other properties should remain unchanged
        assertTrue(updatedContent.contains("<maven.compiler.source>17</maven.compiler.source>"),
                "Other properties should remain unchanged");

        // Verify using helper methods
        assertEquals("25", getJavaVersion(pomPath));
        assertEquals("25", getMavenCompilerRelease(pomPath));
    }

    @Test
    void testAddJavaVersionToExistingProperties() throws Exception {
        // Given a pom.xml with properties section but no java.version
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>

                    <properties>
                        <maven.compiler.source>17</maven.compiler.source>
                    </properties>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When adding java.version
        Step setJavaVersion = new SetJavaVersion(tempDir, "25");
        setJavaVersion.execute();

        // Then java.version should be added
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<java.version>25</java.version>"),
                "java.version should be added");

        // And maven.compiler.release should be added
        assertTrue(updatedContent.contains("<maven.compiler.release>25</maven.compiler.release>"),
                "maven.compiler.release should be added");

        // And existing properties should remain
        assertTrue(updatedContent.contains("<maven.compiler.source>17</maven.compiler.source>"),
                "Existing properties should remain");

        // Verify using helper methods
        assertEquals("25", getJavaVersion(pomPath));
        assertEquals("25", getMavenCompilerRelease(pomPath));
    }

    @Test
    void testCreatePropertiesSectionAndAddJavaVersion() throws Exception {
        // Given a pom.xml without properties section
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When adding java.version
        Step setJavaVersion = new SetJavaVersion(tempDir, "25");
        setJavaVersion.execute();

        // Then properties section and java.version should be added
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<properties>"), "Properties section should be added");
        assertTrue(updatedContent.contains("<java.version>25</java.version>"),
                "java.version should be added");
        assertTrue(updatedContent.contains("<maven.compiler.release>25</maven.compiler.release>"),
                "maven.compiler.release should be added");
        assertTrue(updatedContent.contains("</properties>"), "Properties section should be closed");

        // Verify using helper methods
        assertEquals("25", getJavaVersion(pomPath));
        assertEquals("25", getMavenCompilerRelease(pomPath));
    }

    @Test
    void testCreatePropertiesSectionAfterParent() throws Exception {
        // Given a pom.xml with parent but no properties section
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.0.0</version>
                    </parent>

                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When adding java.version
        Step setJavaVersion = new SetJavaVersion(tempDir, "25");
        setJavaVersion.execute();

        // Then properties section should be added after parent
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<properties>"), "Properties section should be added");
        assertTrue(updatedContent.contains("<java.version>25</java.version>"),
                "java.version should be added");
        assertTrue(updatedContent.contains("<maven.compiler.release>25</maven.compiler.release>"),
                "maven.compiler.release should be added");

        // Properties should come after parent
        int parentIndex = updatedContent.indexOf("</parent>");
        int propertiesIndex = updatedContent.indexOf("<properties>");
        assertTrue(propertiesIndex > parentIndex,
                "Properties section should be after parent section");

        // Verify using helper methods
        assertEquals("25", getJavaVersion(pomPath));
        assertEquals("25", getMavenCompilerRelease(pomPath));
    }

    @Test
    void testGetJavaVersionReturnsNullWhenNotPresent() throws Exception {
        // Given a pom.xml without java.version
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <maven.compiler.source>17</maven.compiler.source>
                    </properties>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When getting java.version
        String version = getJavaVersion(pomPath);

        // Then it should return null
        assertNull(version, "Should return null when java.version is not present");
    }

    @Test
    void testGetJavaVersionReturnsNullWhenPropertiesSectionMissing() throws Exception {
        // Given a pom.xml without properties section
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When getting java.version
        String version = getJavaVersion(pomPath);

        // Then it should return null
        assertNull(version, "Should return null when properties section is missing");
    }

    @Test
    void testSetJavaVersionLogsWarningWhenFileNotFound() throws Exception {
        // Given a directory without pom.xml
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);

        Step setJavaVersion = new SetJavaVersion(emptyDir, "25");

        // When/Then updating should not throw exception (logs warning and returns)
        assertDoesNotThrow(() -> setJavaVersion.execute(),
                "Should not throw exception when pom.xml not found, just log warning");
    }

    @Test
    void testGetJavaVersionThrowsExceptionWhenFileNotFound() {
        // Given a non-existent pom.xml path
        Path pomPath = tempDir.resolve("non-existent-pom.xml");

        // When/Then getting should throw IOException
        assertThrows(IOException.class, () -> getJavaVersion(pomPath),
                "Should throw IOException when file not found");
    }

    @Test
    void testCreatePropertiesSectionBeforeDependencies() throws Exception {
        // Given a pom.xml with dependencies but no properties section
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When adding java.version
        Step setJavaVersion = new SetJavaVersion(tempDir, "25");
        setJavaVersion.execute();

        // Then properties section should be added before dependencies
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<properties>"), "Properties section should be added");
        assertTrue(updatedContent.contains("<java.version>25</java.version>"),
                "java.version should be added");
        assertTrue(updatedContent.contains("<maven.compiler.release>25</maven.compiler.release>"),
                "maven.compiler.release should be added");

        // Properties should come before dependencies
        int propertiesIndex = updatedContent.indexOf("<properties>");
        int dependenciesIndex = updatedContent.indexOf("<dependencies>");
        assertTrue(propertiesIndex > 0 && propertiesIndex < dependenciesIndex,
                "Properties section should be before dependencies section");

        // Verify using helper methods
        assertEquals("25", getJavaVersion(pomPath));
        assertEquals("25", getMavenCompilerRelease(pomPath));
    }

    @Test
    void testSetJavaVersionPreservesXmlStructure() throws Exception {
        // Given a pom.xml with various elements
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                
                    <properties>
                        <java.version>17</java.version>
                        <spring.version>3.0.0</spring.version>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);

        // When updating java.version
        Step setJavaVersion = new SetJavaVersion(tempDir, "25");
        setJavaVersion.execute();

        // Then the structure should be preserved
        String updatedContent = Files.readString(pomPath);
        assertTrue(updatedContent.contains("<java.version>25</java.version>"));
        assertTrue(updatedContent.contains("<maven.compiler.release>25</maven.compiler.release>"));
        assertTrue(updatedContent.contains("<spring.version>3.0.0</spring.version>"));
        assertTrue(updatedContent.contains("<dependencies>"));
        assertTrue(updatedContent.contains("<dependency>"));
    }

    private String getJavaVersion(Path pomPath) throws IOException {
        String content = Files.readString(pomPath);
        Pattern pattern = Pattern.compile("<java\\.version\\s*>([^<]*)</java\\.version>");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String getMavenCompilerRelease(Path pomPath) throws IOException {
        String content = Files.readString(pomPath);
        Pattern pattern = Pattern.compile("<maven\\.compiler\\.release\\s*>([^<]*)</maven\\.compiler\\.release>");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
