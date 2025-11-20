package ch.admin.bit.jeap.cli.migration.step.dockerfile;

import ch.admin.bit.jeap.cli.migration.step.Step;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateDockerfileJavaVersion implements Step {

    private final Path rootDirectory;

    /**
     * Updates Java version to 25 in all Dockerfile* files.
     * <p>
     * The step finds all files matching the pattern "Dockerfile*" in the root directory and updates
     * the Java version in FROM statements to 25 for eclipse-temurin and jeap-runtime-coretto images,
     * regardless of the current version. For example:
     * - FROM eclipse-temurin:17 -> FROM eclipse-temurin:25
     * - FROM eclipse-temurin:21-jdk -> FROM eclipse-temurin:25-jdk
     * - FROM registry.example.com/eclipse-temurin:11-jdk-alpine -> FROM registry.example.com/eclipse-temurin:25-jdk-alpine
     * - FROM jeap-runtime-coretto:17 -> FROM jeap-runtime-coretto:25
     * - FROM ghcr.io/jeap-admin-ch/jeap-runtime-coretto:21-jdk -> FROM ghcr.io/jeap-admin-ch/jeap-runtime-coretto:25-jdk
     * <p>
     * The update works by replacing any version number with "25" at the beginning of the image tag.
     * Does nothing if no Dockerfile* files are found.
     *
     * @param rootDirectory the root directory to search for Dockerfile* files
     */
    public UpdateDockerfileJavaVersion(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    @Override
    public void execute() throws IOException {
        List<Path> dockerfiles = findDockerfiles();

        for (Path dockerfilePath : dockerfiles) {
            updateDockerfile(dockerfilePath);
        }
    }

    private List<Path> findDockerfiles() throws IOException {
        List<Path> dockerfiles = new ArrayList<>();

        if (!Files.isDirectory(rootDirectory)) {
            return dockerfiles;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDirectory, "Dockerfile*")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    dockerfiles.add(entry);
                }
            }
        }

        return dockerfiles;
    }

    private void updateDockerfile(Path dockerfilePath) throws IOException {
        String content = Files.readString(dockerfilePath, StandardCharsets.UTF_8);

        // Pattern to match: FROM <registry/>eclipse-temurin:<version><rest-of-tag>
        //                or FROM <registry/>jeap-runtime-coretto:<version><rest-of-tag>
        // Only updates eclipse-temurin and jeap-runtime-coretto images, replacing any version number with 25
        // The (?:[^:\\s/]+/)* part matches optional registry/path components (e.g., "ghcr.io/jeap-admin-ch/")
        Pattern pattern = Pattern.compile(
                "(FROM\\s+(?:[^:\\s/]+/)*(?:eclipse-temurin|jeap-runtime-coretto)):\\d+([\\s-]|$)",
                Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(content);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1);  // "FROM <registry/>eclipse-temurin" or "FROM <registry/>jeap-runtime-coretto"
            String suffix = matcher.group(2);  // Space, dash, or end of line

            String replacement = prefix + ":25" + suffix;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        Files.writeString(dockerfilePath, result.toString(), StandardCharsets.UTF_8);
    }

    @Override
    public String name() {
        return "Update Dockerfile Java Version";
    }
}
