package ch.admin.bit.jeap.cli.migration.step.mavenwrapper;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    private static final String CURRENT_MAVEN_VERSION = "3.9.12";
    private static final String CURRENT_MAVEN_VERSION_CHECKSUM = "305773a68d6ddfd413df58c82b3f8050e89778e777f3a745c8e5b8cbea4018ef";

    private static final String MAVEN_WRAPPER_PROPS_FILE = ".mvn/wrapper/maven-wrapper.properties";
    private static final String REGEX_TO_REPLACE = "\\d+\\.\\d+\\.\\d+";
    private static final String DISTRIBUTION_SHA_256_SUM = "distributionSha256Sum=";
    private static final String DISTRIBUTION_URL = "distributionUrl=";

    private final Path rootDirectory;

    @Override
    public void execute() throws IOException {
        Path filePath = rootDirectory.resolve(MAVEN_WRAPPER_PROPS_FILE);
        if (Files.exists(rootDirectory.resolve(filePath))) {
            log.info("Updating Maven Wrapper in file: {}", filePath);
            String content = Files.readString(filePath);
            String updatedContent = Arrays.stream(content.split(System.lineSeparator()))
                    .map(this::replaceContent)
                    .collect(Collectors.joining("\n"));
            Files.writeString(filePath, updatedContent + "\n");

            // Update or create jvm.config file
            updateJvmConfigFile(filePath.getParent().getParent());
        }
    }

    private String replaceContent(String line) {
        if (line.startsWith(DISTRIBUTION_URL)) {
            return line.replaceAll(REGEX_TO_REPLACE, CURRENT_MAVEN_VERSION);
        } else if (line.startsWith(DISTRIBUTION_SHA_256_SUM)) {
            return DISTRIBUTION_SHA_256_SUM + CURRENT_MAVEN_VERSION_CHECKSUM;
        }
        return line;
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
        return "Update MavenWrapper version to " + CURRENT_MAVEN_VERSION;
    }

}
