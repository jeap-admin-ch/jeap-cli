package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class UpdateJibBaseImage implements Step {

    private final Path rootDirectory;

    /**
     * Updates jib-maven-plugin base image to Java 25 in all pom.xml files.
     * <p>
     * The step recursively finds all pom.xml files under the root directory and updates
     * the jib-maven-plugin base image tag to 25-al2032-headless if the image name is amazoncorretto.
     * For example:
     * - host:1234/amazoncorretto:21-al2023-headless -> host:1234/amazoncorretto:25-al2032-headless
     * - amazoncorretto:17 -> amazoncorretto:25-al2032-headless
     * - registry.example.com/path/to/amazoncorretto:21-al2023 -> registry.example.com/path/to/amazoncorretto:25-al2032-headless
     * <p>
     * Only updates images where the last part of the image name (ignoring paths/hosts) is amazoncorretto.
     * Does nothing if no pom.xml files are found or if they don't contain jib-maven-plugin with amazoncorretto base image.
     *
     * @param rootDirectory the root directory to search for pom.xml files recursively
     */
    public UpdateJibBaseImage(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    @Override
    public void execute() throws IOException {
        List<Path> pomFiles = findPomFiles();

        for (Path pomPath : pomFiles) {
            updatePomFile(pomPath);
        }
    }

    private List<Path> findPomFiles() throws IOException {
        List<Path> pomFiles = new ArrayList<>();

        if (!Files.isDirectory(rootDirectory)) {
            return pomFiles;
        }

        try (Stream<Path> paths = Files.walk(rootDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .forEach(pomFiles::add);
        }

        return pomFiles;
    }

    private void updatePomFile(Path pomPath) throws IOException {
        String content = Files.readString(pomPath, StandardCharsets.UTF_8);

        // Pattern to match: <from><image>...amazoncorretto:version</image></from>
        // within jib-maven-plugin configuration
        // Captures the full image path and checks if it ends with amazoncorretto before the colon
        Pattern pattern = Pattern.compile(
                "(<from>\\s*<image>)([^<]*/)?(amazoncorretto):[^<]*(</image>\\s*</from>)",
                Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(content);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String openTag = matcher.group(1);      // "<from><image>"
            String imagePath = matcher.group(2);    // "host:1234/" or null
            String imageName = matcher.group(3);    // "amazoncorretto"
            String closeTag = matcher.group(4);     // "</image></from>"

            String replacement = openTag +
                    (imagePath != null ? imagePath : "") +
                    imageName + ":25-al2032-headless" +
                    closeTag;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        // Only write if something changed
        if (!content.equals(result.toString())) {
            Files.writeString(pomPath, result.toString(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public String name() {
        return "Update Jib Base Image";
    }
}
