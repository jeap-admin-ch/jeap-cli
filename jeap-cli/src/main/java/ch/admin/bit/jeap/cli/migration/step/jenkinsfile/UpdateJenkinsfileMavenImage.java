package ch.admin.bit.jeap.cli.migration.step.jenkinsfile;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class UpdateJenkinsfileMavenImage implements Step {

    private final Path rootDirectory;
    private final Map<String, String> imageTagMapping;

    /**
     * Updates the mavenImage attribute in all Jenkinsfile* files based on the provided image tag mapping.
     * <p>
     * The step finds all files matching the pattern "Jenkinsfile*" in the root directory and updates
     * the mavenImage attribute with the Docker image tag based on the image name using the provided mapping.
     * For example, with mapping {"eclipse-temurin": "25", "eclipse-temurin-node": "25-node-22"}:
     * - eclipse-temurin:* -> eclipse-temurin:25
     * - eclipse-temurin-node:* -> eclipse-temurin-node:25-node-22
     * <p>
     * The update works even if the tag value is not on the next line after the image name.
     * Does nothing if no Jenkinsfile* files are found.
     *
     * @param rootDirectory    the root directory to search for Jenkinsfile* files
     * @param imageTagMapping  map of image names to their target tags
     */
    public UpdateJenkinsfileMavenImage(Path rootDirectory, Map<String, String> imageTagMapping) {
        this.rootDirectory = rootDirectory;
        this.imageTagMapping = imageTagMapping;
    }

    @Override
    public void execute() throws IOException {
        List<Path> jenkinsfiles = findJenkinsfiles();

        for (Path jenkinsfilePath : jenkinsfiles) {
            updateJenkinsfile(jenkinsfilePath);
        }
    }

    private List<Path> findJenkinsfiles() throws IOException {
        List<Path> jenkinsfiles = new ArrayList<>();

        if (!Files.isDirectory(rootDirectory)) {
            return jenkinsfiles;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDirectory, "Jenkinsfile*")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    jenkinsfiles.add(entry);
                }
            }
        }

        return jenkinsfiles;
    }

    private void updateJenkinsfile(Path jenkinsfilePath) throws IOException {
        String content = Files.readString(jenkinsfilePath, StandardCharsets.UTF_8);

        // Pattern to match: mavenImage: 'prefix/imageName:tag' or mavenImage: 'imageName:tag'
        // Supports optional whitespace and newlines between components
        Pattern pattern = Pattern.compile(
                "(mavenImage:\\s*['\"])([^/'\":]*/)?(eclipse-temurin(?:-node)?(?:-extras)?):[^'\"]*(['\"])",
                Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(content);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1);        // "mavenImage: '"
            String pathPrefix = matcher.group(2);    // "foo/" or null
            String imageName = matcher.group(3);     // "eclipse-temurin-node" etc.
            String suffix = matcher.group(4);        // "'"

            String newTag = imageTagMapping.get(imageName);
            if (newTag != null) {
                String replacement = prefix +
                        (pathPrefix != null ? pathPrefix : "") +
                        imageName + ":" + newTag +
                        suffix;
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(result);

        if (!content.contentEquals(result)) {
            Files.writeString(jenkinsfilePath, result, StandardCharsets.UTF_8);
            log.info("Updated mavenImage in {}", jenkinsfilePath.getFileName());
        }
    }

    @Override
    public String name() {
        return "Update Jenkinsfile Maven Image";
    }
}
