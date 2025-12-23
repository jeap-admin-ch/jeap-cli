package ch.admin.bit.jeap.cli.migration.step.mavenwrapper;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.migration.utils.MigrationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class UpdateMavenWrapper implements Step {

    private static final String MAVEN_WRAPPER_PROPS_FILE = "maven-wrapper.properties";
    private static final String ACTUAL_MAVEN_VERSION = "3.9.12";
    private static final String REGEX_TO_REPLACE = "\\d+\\.\\d+\\.\\d+";

    private final Path rootDirectory;

    @Override
    public void execute() throws IOException {
        List<File> files = MigrationUtils.findFilesWithNames(rootDirectory, MAVEN_WRAPPER_PROPS_FILE);
        for (File file : files) {
            log.info("Updating Maven Wrapper in file: {}", file.getAbsolutePath());
            Path filePath = file.toPath();
            String content = MigrationUtils.readFileToString(filePath.toString());
            String updatedContent = Arrays.stream(content.split(System.lineSeparator()))
                    .map(line -> line.startsWith("distributionUrl=") ? line.replaceAll(REGEX_TO_REPLACE, ACTUAL_MAVEN_VERSION) : line)
                    .collect(Collectors.joining("\n"));
            // The sha-256 sums are not correct for the new maven version, and not required for an internal repo - remove them
            updatedContent = MigrationUtils.removeLinesContaining(updatedContent, "Sha256Sum");
            Files.writeString(filePath, updatedContent);

            // Update or create jvm.config file
            updateJvmConfigFile(filePath.getParent().getParent());
        }
    }

    /**
     * Update or create the jvm.config file to include necessary JVM options to hide warnings.
     * See
     * <a href="https://developer.mamezou-tech.com/en/blogs/2025/03/30/maven-java24-warning/">Blogpost</a>
     * <a href="https://github.com/apache/maven/issues/10312">Maven Issue</a>
     */
    private void updateJvmConfigFile(Path wrapperRootPath) throws IOException {
        // Add JVM options if not present
        Path jvmOptionsPath = wrapperRootPath.resolve("jvm.config");
        List<String> jvmOptionsLines = new ArrayList<>();
        boolean updated = false;

        if (Files.exists(jvmOptionsPath)) {
            jvmOptionsLines = Files.readAllLines(jvmOptionsPath);
        }
        if (jvmOptionsLines.stream().noneMatch(line -> line.contains("--enable-native-access=ALL-UNNAMED"))) {
            jvmOptionsLines.add("--enable-native-access=ALL-UNNAMED");
            updated = true;
        }
        if (jvmOptionsLines.stream().noneMatch(line -> line.contains("--sun-misc-unsafe-memory-access=allow"))) {
            jvmOptionsLines.add("--sun-misc-unsafe-memory-access=allow");
            updated = true;
        }
        if (updated) {
            log.info("Updating JVM options file: {}", jvmOptionsPath.toAbsolutePath());
            Files.write(jvmOptionsPath, jvmOptionsLines, StandardCharsets.UTF_8);
        }
    }

    @Override
    public String name() {
        return "Update MavenWrapper version to " + ACTUAL_MAVEN_VERSION;
    }

}
