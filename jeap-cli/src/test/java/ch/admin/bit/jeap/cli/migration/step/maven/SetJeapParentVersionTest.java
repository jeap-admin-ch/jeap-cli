package ch.admin.bit.jeap.cli.migration.step.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetJeapParentVersionTest {

    @TempDir
    Path tempDir;

    @Test
    void setsParentVersionToGivenTargetVersion() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Files.writeString(rootPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>jeap-parent</artifactId>
                        <version>34.5.0</version>
                    </parent>
                </project>
                """);

        new SetJeapParentVersion(tempDir, "35.2.0").execute();

        String updated = Files.readString(rootPom);
        assertTrue(updated.contains("<version>35.2.0</version>"));
        assertFalse(updated.contains("<version>34.5.0</version>"));
    }

    @Test
    void isIdempotentWhenVersionAlreadyMatches() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>jeap-parent</artifactId>
                        <version>1.2.3</version>
                    </parent>
                </project>
                """;
        Files.writeString(rootPom, pom);

        new SetJeapParentVersion(tempDir, "1.2.3").execute();

        assertEquals(pom, Files.readString(rootPom));
    }

    @Test
    void doesNothingWhenNoPomExists() throws Exception {
        new SetJeapParentVersion(tempDir, "1.0.0").execute(); // must not throw
    }

    @Test
    void doesNothingWhenNoJeapParentBlock() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.4.0</version>
                    </parent>
                </project>
                """;
        Files.writeString(rootPom, pom);

        new SetJeapParentVersion(tempDir, "1.0.0").execute();

        assertEquals(pom, Files.readString(rootPom));
    }

    @Test
    void mapConstructor_setsVersionForMatchingArtifactId() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Files.writeString(rootPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>jeap-spring-boot-parent</artifactId>
                        <version>33.0.0</version>
                    </parent>
                </project>
                """);

        new SetJeapParentVersion(tempDir, Map.of(
                "jeap-spring-boot-parent", "35.2.0",
                "jeap-internal-spring-boot-parent", "8.2.0"
        )).execute();

        String updated = Files.readString(rootPom);
        assertTrue(updated.contains("<version>35.2.0</version>"));
        assertFalse(updated.contains("<version>33.0.0</version>"));
    }

    @Test
    void mapConstructor_setsVersionForInternalParent() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        Files.writeString(rootPom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>jeap-internal-spring-boot-parent</artifactId>
                        <version>6.0.0</version>
                    </parent>
                </project>
                """);

        new SetJeapParentVersion(tempDir, Map.of(
                "jeap-spring-boot-parent", "35.2.0",
                "jeap-internal-spring-boot-parent", "8.2.0"
        )).execute();

        String updated = Files.readString(rootPom);
        assertTrue(updated.contains("<version>8.2.0</version>"));
        assertFalse(updated.contains("<version>6.0.0</version>"));
    }

    @Test
    void mapConstructor_skipsWhenArtifactIdNotInMap() throws Exception {
        Path rootPom = tempDir.resolve("pom.xml");
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>ch.admin.bit.jeap</groupId>
                        <artifactId>some-other-parent</artifactId>
                        <version>1.0.0</version>
                    </parent>
                </project>
                """;
        Files.writeString(rootPom, pom);

        new SetJeapParentVersion(tempDir, Map.of(
                "jeap-spring-boot-parent", "35.2.0"
        )).execute();

        assertEquals(pom, Files.readString(rootPom));
    }

    private static void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
