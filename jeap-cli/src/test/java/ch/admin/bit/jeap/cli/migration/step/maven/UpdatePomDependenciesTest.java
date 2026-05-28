package ch.admin.bit.jeap.cli.migration.step.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatePomDependenciesTest {

    @TempDir
    Path tempDir;

    @Test
    void replacesGroupAndArtifactWhenFromGroupIdIsSet() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>com.old</groupId>
                            <artifactId>old-lib</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);

        List<DependencyReplacement> replacements = List.of(
                DependencyReplacement.replace("com.old", "old-lib", "com.new", "new-lib"));
        new UpdatePomDependencies(tempDir, Set::of, replacements).execute();

        String updated = Files.readString(pom);
        assertTrue(updated.contains("<groupId>com.new</groupId>"));
        assertTrue(updated.contains("<artifactId>new-lib</artifactId>"));
        assertFalse(updated.contains("<groupId>com.old</groupId>"));
        assertFalse(updated.contains("<artifactId>old-lib</artifactId>"));
    }

    @Test
    void renamesArtifactOnlyWhenFromGroupIdIsNull() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>old-artifact</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        List<DependencyReplacement> replacements = List.of(
                DependencyReplacement.renameArtifact("old-artifact", "new-artifact"));
        new UpdatePomDependencies(tempDir, Set::of, replacements).execute();

        String updated = Files.readString(pom);
        assertTrue(updated.contains("<artifactId>new-artifact</artifactId>"));
        assertFalse(updated.contains("<artifactId>old-artifact</artifactId>"));
        assertTrue(updated.contains("<groupId>org.example</groupId>"), "groupId should be unchanged");
    }

    @Test
    void removesVersionTagFromProjectManagedDependencies() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>commons-io</groupId>
                            <artifactId>commons-io</artifactId>
                            <version>2.18.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        new UpdatePomDependencies(tempDir, () -> Set.of("commons-io:commons-io"), List.of()).execute();

        String updated = Files.readString(pom);
        assertTrue(updated.contains("<artifactId>commons-io</artifactId>"));
        assertFalse(updated.contains("<version>2.18.0</version>"));
    }

    @Test
    void keepsVersionInDependencyManagementBlock() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>commons-io</groupId>
                                <artifactId>commons-io</artifactId>
                                <version>2.18.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);

        new UpdatePomDependencies(tempDir, () -> Set.of("commons-io:commons-io"), List.of()).execute();

        String updated = Files.readString(pom);
        assertTrue(updated.contains("<version>2.18.0</version>"),
                "Version inside <dependencyManagement> must not be removed");
    }

    @Test
    void appliesMultipleReplacementsToSingleFile() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>com.github.tomakehurst</groupId>
                            <artifactId>wiremock-jre8-standalone</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-aop</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        List<DependencyReplacement> replacements = List.of(
                DependencyReplacement.replace("com.github.tomakehurst", "wiremock-jre8-standalone",
                        "org.wiremock", "wiremock-standalone"),
                DependencyReplacement.renameArtifact("spring-boot-starter-aop", "spring-boot-starter-aspectj")
        );
        new UpdatePomDependencies(tempDir, Set::of, replacements).execute();

        String updated = Files.readString(pom);
        assertTrue(updated.contains("<groupId>org.wiremock</groupId>"));
        assertTrue(updated.contains("<artifactId>wiremock-standalone</artifactId>"));
        assertTrue(updated.contains("<artifactId>spring-boot-starter-aspectj</artifactId>"));
        assertFalse(updated.contains("wiremock-jre8-standalone"));
        assertFalse(updated.contains("spring-boot-starter-aop"));
    }

    @Test
    void doesNotRemoveVersionFromPluginDependency() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <dependencies>
                                    <dependency>
                                        <groupId>commons-io</groupId>
                                        <artifactId>commons-io</artifactId>
                                        <version>2.18.0</version>
                                    </dependency>
                                </dependencies>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);

        new UpdatePomDependencies(tempDir, () -> Set.of("commons-io:commons-io"), List.of()).execute();

        String updated = Files.readString(pom);
        assertTrue(updated.contains("<version>2.18.0</version>"),
                "Version inside <plugin><dependencies> must not be removed");
    }

    @Test
    void removesDependencyBlockEntirely() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.testcontainers</groupId>
                            <artifactId>testcontainers</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.testcontainers</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);

        new UpdatePomDependencies(tempDir, Set::of, List.of(),
                List.of("org.testcontainers:junit-jupiter")).execute();

        String updated = Files.readString(pom);
        assertFalse(updated.contains("<artifactId>junit-jupiter</artifactId>"),
                "junit-jupiter dependency block must be removed");
        assertTrue(updated.contains("<artifactId>testcontainers</artifactId>"),
                "Other testcontainers dependency must remain");
        assertFalse(updated.contains("\n\n\n"),
                "No triple blank lines after removal");
    }

    @Test
    void doesNotRemoveDependencyFromDependencyManagement() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.testcontainers</groupId>
                                <artifactId>junit-jupiter</artifactId>
                                <version>1.21.3</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);

        new UpdatePomDependencies(tempDir, Set::of, List.of(),
                List.of("org.testcontainers:junit-jupiter")).execute();

        String updated = Files.readString(pom);
        assertTrue(updated.contains("<artifactId>junit-jupiter</artifactId>"),
                "Dependency inside <dependencyManagement> must not be removed");
    }

    @Test
    void doesNotRenameArtifactInProjectDeclarationOrParentBlock() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>my-lib</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>my-lib</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>my-lib</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        List<DependencyReplacement> replacements = List.of(
                DependencyReplacement.renameArtifact("my-lib", "my-lib-renamed"));
        new UpdatePomDependencies(tempDir, Set::of, replacements).execute();

        String updated = Files.readString(pom);
        // Only the <dependency> block should be renamed
        assertTrue(updated.contains("<artifactId>my-lib-renamed</artifactId>"),
                "Artifact inside <dependency> block must be renamed");
        // The <parent> and project own <artifactId> must stay untouched
        long occurrences = updated.lines()
                .filter(l -> l.contains("<artifactId>my-lib</artifactId>"))
                .count();
        assertEquals(2, occurrences,
                "<parent> and project <artifactId> must not be renamed");
    }
}
