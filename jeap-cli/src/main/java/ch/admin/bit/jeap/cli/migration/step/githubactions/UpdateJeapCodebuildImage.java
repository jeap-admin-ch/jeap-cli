package ch.admin.bit.jeap.cli.migration.step.githubactions;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class UpdateJeapCodebuildImage implements Step {

    private final Path rootDirectory;
    private final String imageTag;

    /**
     * Updates codebuild-image tags in GitHub Actions workflow files that use jeap-github-actions.
     * <p>
     * This step finds all workflow files in .github/workflows/ directory, checks if they reference
     * jeap-github-actions workflows, and updates the codebuild-image parameter to the specified tag
     * if the image name starts with "jeap-codebuild-java".
     * <p>
     * For example with imageTag "25-node-22":
     * - jeap-codebuild-java:21-node-22 -> jeap-codebuild-java:25-node-22
     * - jeap-codebuild-java:17 -> jeap-codebuild-java:25-node-22
     * - jeap-codebuild-java-special:21 -> jeap-codebuild-java-special:25-node-22
     * <p>
     * Images not starting with "jeap-codebuild-java" (e.g., "jeap-codebuild", "other-image") are not modified.
     * <p>
     * Does nothing if no workflow files are found or if they don't use jeap-github-actions.
     *
     * @param rootDirectory the root directory to search for .github/workflows/ files
     * @param imageTag      the image tag to set (e.g., "25-node-22")
     */
    public UpdateJeapCodebuildImage(Path rootDirectory, String imageTag) {
        this.rootDirectory = rootDirectory;
        this.imageTag = imageTag;
    }

    @Override
    public void execute() throws IOException {
        Path workflowsDir = rootDirectory.resolve(".github/workflows");
        if (!Files.isDirectory(workflowsDir)) {
            log.debug("No .github/workflows directory found at {}", workflowsDir);
            return;
        }

        List<Path> workflowFiles = findWorkflowFiles(workflowsDir);
        if (workflowFiles.isEmpty()) {
            log.debug("No workflow files found in {}", workflowsDir);
            return;
        }

        for (Path workflowFile : workflowFiles) {
            updateWorkflowFile(workflowFile);
        }
    }

    private List<Path> findWorkflowFiles(Path workflowsDir) throws IOException {
        List<Path> workflowFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workflowsDir, "*.{yml,yaml}")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    workflowFiles.add(entry);
                }
            }
        }
        return workflowFiles;
    }

    private void updateWorkflowFile(Path workflowFile) throws IOException {
        String content = Files.readString(workflowFile, StandardCharsets.UTF_8);

        // Only process files that use jeap-github-actions
        if (!content.contains("jeap-github-actions")) {
            log.debug("Workflow file {} does not use jeap-github-actions, skipping", workflowFile.getFileName());
            return;
        }

        // Pattern to match: codebuild-image: "jeap-codebuild-java...:anything"
        // or codebuild-image: 'jeap-codebuild-java...:anything'
        // Captures: quote, image name prefix, and quote
        Pattern pattern = Pattern.compile(
                "(codebuild-image:\\s*['\"])(jeap-codebuild-java[^:]*:)[^'\"]*(['\"])",
                Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(content);
        StringBuilder result = new StringBuilder();
        boolean modified = false;

        while (matcher.find()) {
            String prefix = matcher.group(1);      // "codebuild-image: '"
            String imageName = matcher.group(2);   // "jeap-codebuild-java:" or "jeap-codebuild-java-special:"
            String quote = matcher.group(3);       // "'"

            String replacement = prefix + imageName + imageTag + quote;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            modified = true;
            log.debug("Updated codebuild-image in {} to use tag {}", workflowFile.getFileName(), imageTag);
        }
        matcher.appendTail(result);

        if (modified) {
            Files.writeString(workflowFile, result.toString(), StandardCharsets.UTF_8);
            log.info("Updated codebuild-image in {}", workflowFile.getFileName());
        }
    }

    @Override
    public String name() {
        return "Update jEAP Codebuild Image";
    }
}
