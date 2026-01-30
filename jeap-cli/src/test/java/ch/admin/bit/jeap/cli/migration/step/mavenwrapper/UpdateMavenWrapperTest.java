package ch.admin.bit.jeap.cli.migration.step.mavenwrapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class UpdateMavenWrapperTest {

    @Test
    void shouldUpdateMavenVersionAndCreateJvmConfig(@TempDir Path tempDir) throws Exception {
        Path fileFolder = tempDir.resolve(".mvn").resolve("wrapper");
        Files.createDirectories(fileFolder);
        Path filePath = fileFolder.resolve("maven-wrapper.properties");
        Files.writeString(filePath, """
                distributionUrl=https://repo.bit.admin.ch/repository/maven-central/org/apache/maven/apache-maven/3.8.6/apache-maven-3.8.6-bin.zip
                distributionSha256Sum=foobar
                wrapperSha256Sum=foobar
                """);

        UpdateMavenWrapper migration = new UpdateMavenWrapper(tempDir);
        migration.execute();

        String result = Files.readString(filePath);
        assertThat(result).isEqualTo("""
                distributionUrl=https://repo.bit.admin.ch/repository/maven-central/org/apache/maven/apache-maven/3.9.12/apache-maven-3.9.12-bin.zip
                distributionSha256Sum=foobar
                wrapperSha256Sum=foobar
                """);

        Path jvmConfigPath = tempDir.resolve(".mvn").resolve("jvm.config");
        assertTrue(Files.exists(jvmConfigPath), "jvm.config file should be created");
        String jvmConfigContent = Files.readString(jvmConfigPath);
        assertThat(jvmConfigContent)
                .contains("--enable-native-access=ALL-UNNAMED")
                .contains("--sun-misc-unsafe-memory-access=allow");
    }

    @Test
    void shouldUpdateAnyMavenVersion(@TempDir Path tempDir) throws Exception {
        Path fileFolder = tempDir.resolve(".mvn").resolve("wrapper");
        Files.createDirectories(fileFolder);
        Path filePath = fileFolder.resolve("maven-wrapper.properties");
        Files.writeString(filePath, "distributionUrl=https://repo.bit.admin.ch/repository/maven-central/org/apache/maven/apache-maven/3.128.66/apache-maven-3.128.66-bin.zip");

        UpdateMavenWrapper migration = new UpdateMavenWrapper(tempDir);
        migration.execute();

        String result = Files.readString(filePath);
        assertEquals(
                "distributionUrl=https://repo.bit.admin.ch/repository/maven-central/org/apache/maven/apache-maven/3.9.12/apache-maven-3.9.12-bin.zip\n",
                result);
    }

    @Test
    void shouldOnlyUpdateDistributionUrlVersionAndNotWrapperVersion(@TempDir Path tempDir) throws Exception {
        Path fileFolder = tempDir.resolve(".mvn").resolve("wrapper");
        Files.createDirectories(fileFolder);
        Path filePath = fileFolder.resolve("maven-wrapper.properties");
        Files.writeString(filePath, """
                wrapperVersion=3.3.4
                wrapperUrl=https://repo.bit.admin.ch/repository/maven-public/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar
                distributionUrl=https://repo.bit.admin.ch/repository/maven-central/org/apache/maven/apache-maven/3.8.6/apache-maven-3.8.6-bin.zip
                """);

        UpdateMavenWrapper migration = new UpdateMavenWrapper(tempDir);
        migration.execute();

        String result = Files.readString(filePath);
        assertEquals("""
                wrapperVersion=3.3.4
                wrapperUrl=https://repo.bit.admin.ch/repository/maven-public/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar
                distributionUrl=https://repo.bit.admin.ch/repository/maven-central/org/apache/maven/apache-maven/3.9.12/apache-maven-3.9.12-bin.zip
                """, result);
    }

    @Test
    void shouldPreserveExistingJvmConfigOptionsWhenAddingNewOnes(@TempDir Path tempDir) throws Exception {
        Path fileFolder = tempDir.resolve(".mvn").resolve("wrapper");
        Files.createDirectories(fileFolder);
        Path filePath = fileFolder.resolve("maven-wrapper.properties");
        Files.writeString(filePath, """
                distributionUrl=https://repo.bit.admin.ch/repository/maven-central/org/apache/maven/apache-maven/3.8.6/apache-maven-3.8.6-bin.zip
                distributionSha256Sum=foobar
                wrapperSha256Sum=foobar
                """);

        Path mvnFolder = tempDir.resolve(".mvn");
        Path jvmConfigFile = mvnFolder.resolve("jvm.config");
        Files.writeString(jvmConfigFile, """
                --foobar=baz
                """);

        UpdateMavenWrapper migration = new UpdateMavenWrapper(tempDir);
        migration.execute();

        String result = Files.readString(filePath);
        assertThat(result).isEqualTo("""
                distributionUrl=https://repo.bit.admin.ch/repository/maven-central/org/apache/maven/apache-maven/3.9.12/apache-maven-3.9.12-bin.zip
                distributionSha256Sum=foobar
                wrapperSha256Sum=foobar
                """);

        Path jvmConfigPath = tempDir.resolve(".mvn").resolve("jvm.config");
        assertTrue(Files.exists(jvmConfigPath), "jvm.config file should be created");
        String jvmConfigContent = Files.readString(jvmConfigPath);
        assertThat(jvmConfigContent)
                .contains("--foobar=baz")
                .contains("--enable-native-access=ALL-UNNAMED")
                .contains("--sun-misc-unsafe-memory-access=allow");
    }

    @Test
    void shouldDoNothingIfMavenWrapperPropertiesAbsent(@TempDir Path tempDir) {
        UpdateMavenWrapper migration = new UpdateMavenWrapper(tempDir);
        assertDoesNotThrow(migration::execute);
    }
}
