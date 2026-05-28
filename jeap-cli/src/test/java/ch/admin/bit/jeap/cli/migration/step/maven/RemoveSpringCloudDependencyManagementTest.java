package ch.admin.bit.jeap.cli.migration.step.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RemoveSpringCloudDependencyManagementTest {

    @TempDir
    Path tempDir;

    // --- removeSpringCloudDependencyBlock ---

    @Test
    void removesSpringCloudDependencyBlockFromDependencyManagement() {
        String input = """
                <project>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.springframework.cloud</groupId>
                        <artifactId>spring-cloud-dependencies</artifactId>
                        <version>2025.1.1</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """;

        String result = new RemoveSpringCloudDependencyManagement(tempDir)
                .removeSpringCloudDependencyBlock(input);

        assertFalse(result.contains("spring-cloud-dependencies"));
        assertFalse(result.contains("org.springframework.cloud"));
    }

    @Test
    void doesNotRemoveOtherDependencyManagementEntries() {
        String input = """
                <project>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.springframework.cloud</groupId>
                        <artifactId>spring-cloud-dependencies</artifactId>
                        <version>2025.1.1</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                      <dependency>
                        <groupId>com.other</groupId>
                        <artifactId>other-bom</artifactId>
                        <version>1.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """;

        String result = new RemoveSpringCloudDependencyManagement(tempDir)
                .removeSpringCloudDependencyBlock(input);

        assertFalse(result.contains("spring-cloud-dependencies"));
        assertTrue(result.contains("other-bom"));
        assertTrue(result.contains("com.other"));
    }

    @Test
    void doesNotRemoveSpringCloudFromRegularDependencies() {
        // If spring-cloud-dependencies appears outside <dependencyManagement>, leave it alone
        String input = """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.cloud</groupId>
                      <artifactId>spring-cloud-dependencies</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """;

        String result = new RemoveSpringCloudDependencyManagement(tempDir)
                .removeSpringCloudDependencyBlock(input);

        assertEquals(input, result, "Should not modify dependencies outside dependencyManagement");
    }

    @Test
    void returnsUnchangedWhenSpringCloudNotPresent() {
        String input = """
                <project>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.other</groupId>
                        <artifactId>other-bom</artifactId>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """;

        String result = new RemoveSpringCloudDependencyManagement(tempDir)
                .removeSpringCloudDependencyBlock(input);

        assertEquals(input, result);
    }

    // --- removeEmptyDependencyManagementBlock ---

    @Test
    void removesEmptyDependencyManagementBlock() {
        String input = """
                <project>
                  <dependencyManagement>
                    <dependencies>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """;

        String result = new RemoveSpringCloudDependencyManagement(tempDir)
                .removeEmptyDependencyManagementBlock(input);

        assertFalse(result.contains("<dependencyManagement>"));
        assertFalse(result.contains("</dependencyManagement>"));
    }

    @Test
    void doesNotRemoveDependencyManagementBlockWithContent() {
        String input = """
                <project>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.other</groupId>
                        <artifactId>other-bom</artifactId>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """;

        String result = new RemoveSpringCloudDependencyManagement(tempDir)
                .removeEmptyDependencyManagementBlock(input);

        assertEquals(input, result);
    }

    // --- full execute() end-to-end ---

    @Test
    void executeRemovesBlockAndEmptyWrapperFromPomFile() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.springframework.cloud</groupId>
                        <artifactId>spring-cloud-dependencies</artifactId>
                        <version>2025.1.1</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);

        new RemoveSpringCloudDependencyManagement(tempDir).execute();

        String updated = Files.readString(pom);
        assertFalse(updated.contains("spring-cloud-dependencies"));
        assertFalse(updated.contains("<dependencyManagement>"));
    }

    @Test
    void executeKeepsOtherEntriesAndRemovesOnlySpringCloud() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.springframework.cloud</groupId>
                        <artifactId>spring-cloud-dependencies</artifactId>
                        <version>2025.1.1</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                      <dependency>
                        <groupId>com.squareup.okhttp3</groupId>
                        <artifactId>okhttp-bom</artifactId>
                        <version>5.0.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);

        new RemoveSpringCloudDependencyManagement(tempDir).execute();

        String updated = Files.readString(pom);
        assertFalse(updated.contains("spring-cloud-dependencies"));
        assertTrue(updated.contains("okhttp-bom"));
        assertTrue(updated.contains("<dependencyManagement>"));
    }

    @Test
    void executeSkipsWhenRootDirectoryDoesNotExist() throws Exception {
        Path nonExistent = tempDir.resolve("does-not-exist");
        // Should not throw
        assertDoesNotThrow(() -> new RemoveSpringCloudDependencyManagement(nonExistent).execute());
    }
}
