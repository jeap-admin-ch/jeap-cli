package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrepareForSpringBoot4ParentUpgradeTest {

    @TempDir
    Path tempDir;

    @Test
    void executesSubStepsInOrder() throws Exception {
        List<String> executed = new ArrayList<>();
        PrepareForSpringBoot4ParentUpgrade step = new PrepareForSpringBoot4ParentUpgrade(List.of(
                namedStep("one", executed),
                namedStep("two", executed),
                namedStep("three", executed)
        ));

        step.execute();

        assertEquals(List.of("one", "two", "three"), executed);
    }

    @Test
    void addsProjectLevelDependencyManagementAndRemovesLocalVersionsInModules() throws Exception {
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
                    <modules>
                        <module>module-a</module>
                    </modules>
                </project>
                """);

        Files.writeString(modulePom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>root</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>module-a</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>commons-io</groupId>
                            <artifactId>commons-io</artifactId>
                            <version>2.18.0</version>
                        </dependency>
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

        createStep(Map.of("commons-io:commons-io", "2.99.0")).execute();

        String updatedRootPom = Files.readString(rootPom);
        String updatedModulePom = Files.readString(modulePom);

        assertTrue(updatedRootPom.contains("<dependencyManagement>"));
        assertTrue(updatedRootPom.contains("<groupId>commons-io</groupId>"));
        assertTrue(updatedRootPom.contains("<artifactId>commons-io</artifactId>"));
        assertTrue(updatedRootPom.contains("<version>2.99.0</version>"));
        assertTrue(updatedRootPom.contains("TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management"));

        assertFalse(updatedModulePom.contains("<artifactId>commons-io</artifactId>\n            <version>2.18.0</version>"));
        assertTrue(updatedModulePom.contains("<groupId>org.wiremock</groupId>"));
        assertTrue(updatedModulePom.contains("<artifactId>wiremock-standalone</artifactId>"));
        assertTrue(updatedModulePom.contains("<artifactId>spring-boot-starter-aspectj</artifactId>"));
        assertFalse(updatedModulePom.contains("<artifactId>spring-boot-starter-aop</artifactId>"));
    }

    @Test
    void keepsExistingManagedDependencyAndDoesNotDuplicateIt() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Path moduleDir = tempDir.resolve("module-b");
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

        Files.writeString(modulePom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>module-b</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>commons-io</groupId>
                            <artifactId>commons-io</artifactId>
                            <version>2.18.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        createStep(Map.of("commons-io:commons-io", "2.99.0")).execute();

        String updatedRootPom = Files.readString(rootPom);
        String updatedModulePom = Files.readString(modulePom);

        assertEquals(1, countCommonsIoArtifactIds(updatedRootPom));
        assertTrue(updatedRootPom.contains("<version>2.17.0</version>"));
        assertFalse(updatedModulePom.contains("<version>2.18.0</version>"));
    }

    private int countCommonsIoArtifactIds(String text) {
        Matcher matcher = Pattern.compile(Pattern.quote("<artifactId>commons-io</artifactId>")).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private PrepareForSpringBoot4ParentUpgrade createStep(Map<String, String> resolvedVersions) {
        return new PrepareForSpringBoot4ParentUpgrade(tempDir,
                (groupId, artifactId) -> Optional.ofNullable(resolvedVersions.get(groupId + ":" + artifactId)));
    }

    private Step namedStep(String name, List<String> executed) {
        return () -> executed.add(name);
    }
}


