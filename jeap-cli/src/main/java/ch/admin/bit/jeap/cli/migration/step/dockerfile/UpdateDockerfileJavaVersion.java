package ch.admin.bit.jeap.cli.migration.step.dockerfile;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class UpdateDockerfileJavaVersion implements Step {

    private final Path rootDirectory;
    private final String imageName;
    private final String imageTag;

    /**
     * Updates image tags in all Dockerfile* files for a specific Docker image.
     * <p>
     * The step finds all files matching the pattern "Dockerfile*" in the root directory and updates
     * the image tag in FROM statements for the specified image name (ignoring registry host/path). For example:
     * - FROM eclipse-temurin:17 -> FROM eclipse-temurin:25 (if imageName is "eclipse-temurin" and imageTag is "25")
     * - FROM eclipse-temurin:21-jdk -> FROM eclipse-temurin:25 (if imageName is "eclipse-temurin" and imageTag is "25")
     * - FROM registry.example.com/eclipse-temurin:11-jdk-alpine -> FROM registry.example.com/eclipse-temurin:25 (if imageName is "eclipse-temurin" and imageTag is "25")
     * - FROM jeap-runtime-coretto:21 -> FROM jeap-runtime-coretto:25 (if imageName is "jeap-runtime-coretto" and imageTag is "25")
     * <p>
     * The imageTag parameter should be the complete image tag you want to use.
     * Does nothing if no Dockerfile* files are found.
     *
     * @param rootDirectory the root directory to search for Dockerfile* files
     * @param imageName     the image name to match (e.g., "eclipse-temurin", "jeap-runtime-coretto")
     * @param imageTag      the complete image tag to set (e.g., "25", "25-jdk", "25-jre")
     */
    public UpdateDockerfileJavaVersion(Path rootDirectory, String imageName, String imageTag) {
        this.rootDirectory = rootDirectory;
        this.imageName = imageName;
        this.imageTag = imageTag;
    }

    @Override
    public void execute() throws IOException {
        var dockerfiles = findDockerfiles();

        for (Path dockerfilePath : dockerfiles) {
            updateDockerfile(dockerfilePath);
        }
    }

    private List<Path> findDockerfiles() throws IOException {
        try (var stream = Files.walk(rootDirectory)) {
            return stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith("Dockerfile"))
                    .toList();
        }
    }

    private void updateDockerfile(Path dockerfilePath) throws IOException {
        String content = Files.readString(dockerfilePath, StandardCharsets.UTF_8);

        // Pattern to match: FROM <registry/><imageName>:<tag>
        // Where <tag> is everything after the colon until whitespace or end of line
        // The (?:[^:\\s/]+/)* part matches optional registry/path components (e.g., "ghcr.io/jeap-admin-ch/")
        String escapedImageName = Pattern.quote(imageName);
        Pattern pattern = Pattern.compile(
                "(FROM\\s+(?:[^:\\s/]+/)*" + escapedImageName + "):[^\\s]+(\\s|$)"
        );

        Matcher matcher = pattern.matcher(content);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1);        // "FROM <registry/><imageName>"
            String trailing = matcher.group(2);      // Space or end of line

            String replacement = prefix + ":" + imageTag + trailing;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        if (!content.contains(result)) {
            Files.writeString(dockerfilePath, result.toString(), StandardCharsets.UTF_8);
            log.info("Updated {} in {}", imageName, dockerfilePath.getFileName());
        }
    }

    @Override
    public String name() {
        return "Update Dockerfile " + imageName + " to " + imageTag;
    }
}
