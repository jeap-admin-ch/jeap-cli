package ch.admin.bit.jeap.cli.migration.step.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnsureProjectDependencyManagementTest {

    @TempDir
    Path tempDir;

    @Test
    void addsMissingDependencyManagementEntries() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Path moduleDir = tempDir.resolve("module-a");
        Files.createDirectories(moduleDir);
        Path modulePom = moduleDir.resolve("pom.xml");

        Files.writeString(rootPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>ch.admin.bit.jeap</groupId>
                    <artifactId>root</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                </project>
                """);

        Files.writeString(modulePom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>module-a</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>commons-io</groupId>
                            <artifactId>commons-io</artifactId>
                            <version>2.18.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        EnsureProjectDependencyManagement step = createStep(
                List.of("commons-io:commons-io"),
                Map.of("commons-io:commons-io", "2.99.0"));

        step.execute();

        String updatedRootPom = Files.readString(rootPom);
        assertTrue(updatedRootPom.contains("<dependencyManagement>"));
        assertTrue(updatedRootPom.contains("<groupId>commons-io</groupId>"));
        assertTrue(updatedRootPom.contains("<version>2.99.0</version>"));
        assertTrue(step.projectManagedDependencies().contains("commons-io:commons-io"));
    }

    @Test
    void doesNotDuplicateAlreadyManagedDependency() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Files.writeString(rootPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>commons-io</groupId>
                                <artifactId>commons-io</artifactId>
                                <version>2.17.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);

        EnsureProjectDependencyManagement step = createStep(
                List.of("commons-io:commons-io"),
                Map.of("commons-io:commons-io", "2.99.0"));

        step.execute();

        String updatedRootPom = Files.readString(rootPom);
        long count = updatedRootPom.lines()
                .filter(l -> l.contains("<artifactId>commons-io</artifactId>"))
                .count();
        assertEquals(1, count, "commons-io must appear exactly once in dependencyManagement");
        assertFalse(updatedRootPom.contains("<version>2.99.0</version>"),
                "Existing version must not be overwritten");
    }

    @Test
    void tracksProjectManagedDependencies() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Files.writeString(rootPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <packaging>pom</packaging>
                </project>
                """);

        EnsureProjectDependencyManagement step = createStep(
                List.of("com.example:lib-a", "com.example:lib-b"),
                Map.of("com.example:lib-a", "1.0.0", "com.example:lib-b", "2.0.0"));

        step.execute();

        assertTrue(step.projectManagedDependencies().contains("com.example:lib-a"));
        assertTrue(step.projectManagedDependencies().contains("com.example:lib-b"));
    }

    @Test
    void addsDependencyManagementAtProjectLevelWhenOnlyPluginDependenciesExist() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Files.writeString(rootPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>root</artifactId>
                    <version>1.0.0</version>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.example</groupId>
                                <artifactId>example-plugin</artifactId>
                                <dependencies>
                                    <dependency>
                                        <groupId>com.example</groupId>
                                        <artifactId>plugin-library</artifactId>
                                        <version>1.0.0</version>
                                    </dependency>
                                </dependencies>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);

        createStep(List.of("commons-io:commons-io"), Map.of("commons-io:commons-io", "2.99.0")).execute();

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rootPom.toFile());
        assertEquals(1, document.getElementsByTagName("dependencyManagement").getLength());
        assertSame(document.getDocumentElement(),
                document.getElementsByTagName("dependencyManagement").item(0).getParentNode());
    }

    @Test
    void addsDependencyManagementAlongsideExistingProjectDependencies() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Files.writeString(rootPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>root</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>application-library</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        createStep(List.of("commons-io:commons-io"), Map.of("commons-io:commons-io", "2.99.0")).execute();

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rootPom.toFile());
        Node rootDependencyManagement = findRootDependencyManagement(document);
        assertTrue(rootDependencyManagement.getTextContent().contains("commons-io"));
        assertTrue(Files.readString(rootPom).contains("<artifactId>application-library</artifactId>"));
    }

    @Test
    void addsRootDependencyManagementWhenOnlyProfileDependencyManagementExists() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Files.writeString(rootPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>root</artifactId>
                    <version>1.0.0</version>
                    <profiles>
                        <profile>
                            <id>example</id>
                            <dependencyManagement>
                                <dependencies>
                                    <dependency>
                                        <groupId>commons-io</groupId>
                                        <artifactId>commons-io</artifactId>
                                        <version>2.17.0</version>
                                    </dependency>
                                </dependencies>
                            </dependencyManagement>
                        </profile>
                    </profiles>
                </project>
                """);

        createStep(List.of("commons-io:commons-io"), Map.of("commons-io:commons-io", "2.99.0")).execute();

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rootPom.toFile());
        assertEquals(2, document.getElementsByTagName("dependencyManagement").getLength());
        assertTrue(findRootDependencyManagement(document).getTextContent().contains("2.99.0"));
        assertTrue(Files.readString(rootPom).contains("<version>2.17.0</version>"));
    }

    private Node findRootDependencyManagement(Document document) {
        NodeList dependencyManagementElements = document.getElementsByTagName("dependencyManagement");
        for (int i = 0; i < dependencyManagementElements.getLength(); i++) {
            Node dependencyManagement = dependencyManagementElements.item(i);
            if (dependencyManagement.getParentNode() == document.getDocumentElement()) {
                return dependencyManagement;
            }
        }
        throw new AssertionError("No root dependencyManagement element found");
    }

    private EnsureProjectDependencyManagement createStep(List<String> dependenciesToManage,
                                                         Map<String, String> resolvedVersions) {
        EnsureProjectDependencyManagement.DependencyVersionResolver resolver =
                (groupId, artifactId) -> Optional.ofNullable(resolvedVersions.get(groupId + ":" + artifactId));
        return new EnsureProjectDependencyManagement(tempDir, dependenciesToManage, resolver);
    }
}
