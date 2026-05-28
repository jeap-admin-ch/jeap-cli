package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Removes the {@code spring-cloud-dependencies} BOM import from all pom.xml files.
 * <p>
 * Spring Cloud 2025 is now managed by the jEAP Spring Boot 4 parent BOM, so an explicit
 * {@code <dependencyManagement>} import of {@code spring-cloud-dependencies} is redundant
 * and may cause version conflicts.
 * <p>
 * This step removes the {@code <dependency>} block for
 * {@code org.springframework.cloud:spring-cloud-dependencies} from every
 * {@code <dependencyManagement>} section it finds. If the surrounding
 * {@code <dependencyManagement>} block becomes empty after the removal, the whole block
 * is removed as well.
 */
@Slf4j
public class RemoveSpringCloudDependencyManagement implements Step {

    private static final String SPRING_CLOUD_GROUP_ID = "org.springframework.cloud";
    private static final String SPRING_CLOUD_ARTIFACT_ID = "spring-cloud-dependencies";

    // Matches a full <dependency>...</dependency> block (non-greedy, across lines)
    private static final Pattern DEPENDENCY_BLOCK_PATTERN = Pattern.compile(
            "<dependency>(.*?)</dependency>", Pattern.DOTALL);

    // Matches <groupId>org.springframework.cloud</groupId> (with optional whitespace)
    private static final Pattern CLOUD_GROUP_PATTERN = Pattern.compile(
            "<groupId>\\s*" + Pattern.quote(SPRING_CLOUD_GROUP_ID) + "\\s*</groupId>");

    // Matches <artifactId>spring-cloud-dependencies</artifactId> (with optional whitespace)
    private static final Pattern CLOUD_ARTIFACT_PATTERN = Pattern.compile(
            "<artifactId>\\s*" + Pattern.quote(SPRING_CLOUD_ARTIFACT_ID) + "\\s*</artifactId>");

    // Matches an empty <dependencyManagement> block (only whitespace / empty <dependencies> inside)
    private static final Pattern EMPTY_DEP_MGMT_PATTERN = Pattern.compile(
            "\\s*<dependencyManagement>\\s*<dependencies>\\s*</dependencies>\\s*</dependencyManagement>",
            Pattern.DOTALL);

    private final Path rootDirectory;

    public RemoveSpringCloudDependencyManagement(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    @Override
    public void execute() throws IOException {
        if (!Files.exists(rootDirectory)) {
            return;
        }
        try (Stream<Path> stream = Files.find(
                rootDirectory,
                Integer.MAX_VALUE,
                (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().equals("pom.xml"))) {
            for (Path pom : stream.toList()) {
                updatePomFile(pom);
            }
        }
    }

    private void updatePomFile(Path pomPath) throws IOException {
        String original = Files.readString(pomPath, StandardCharsets.UTF_8);
        String updated = removeSpringCloudDependencyBlock(original);
        updated = removeEmptyDependencyManagementBlock(updated);

        if (!updated.equals(original)) {
            Files.writeString(pomPath, updated, StandardCharsets.UTF_8);
            log.info("Removed spring-cloud-dependencies dependencyManagement from {}", pomPath);
        }
    }

    /**
     * Removes the {@code <dependency>} block for {@code spring-cloud-dependencies} from inside
     * any {@code <dependencyManagement>} section, including its leading indentation and trailing newline.
     */
    String removeSpringCloudDependencyBlock(String content) {
        Matcher matcher = DEPENDENCY_BLOCK_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        boolean changed = false;

        while (matcher.find()) {
            String block = matcher.group(0);
            int blockStart = matcher.start();

            if (isSpringCloudDependency(block) && isInsideDependencyManagement(content, blockStart)) {
                // Strip leading whitespace on the same line (indentation)
                int wsStart = blockStart;
                while (wsStart > lastEnd && content.charAt(wsStart - 1) != '\n') {
                    wsStart--;
                }
                sb.append(content, lastEnd, wsStart);
                lastEnd = matcher.end();
                // Consume trailing newline so no blank line is left behind
                if (lastEnd < content.length() && content.charAt(lastEnd) == '\n') {
                    lastEnd++;
                }
                changed = true;
                log.debug("Removed {}:{} dependency block from {}", SPRING_CLOUD_GROUP_ID, SPRING_CLOUD_ARTIFACT_ID, "dependencyManagement");
            } else {
                sb.append(content, lastEnd, matcher.end());
                lastEnd = matcher.end();
            }
        }
        sb.append(content, lastEnd, content.length());
        return changed ? sb.toString() : content;
    }

    private boolean isSpringCloudDependency(String block) {
        return CLOUD_GROUP_PATTERN.matcher(block).find()
                && CLOUD_ARTIFACT_PATTERN.matcher(block).find();
    }

    private boolean isInsideDependencyManagement(String content, int index) {
        int openIndex = content.lastIndexOf("<dependencyManagement>", index);
        int closeIndex = content.lastIndexOf("</dependencyManagement>", index);
        return openIndex >= 0 && closeIndex < openIndex;
    }

    /**
     * Removes a {@code <dependencyManagement>} block that contains only whitespace
     * (i.e. the {@code <dependencies>} child is empty after prior removals).
     */
    String removeEmptyDependencyManagementBlock(String content) {
        String result = EMPTY_DEP_MGMT_PATTERN.matcher(content).replaceAll("");
        if (!result.equals(content)) {
            log.debug("Removed empty <dependencyManagement> block");
        }
        return result;
    }

    @Override
    public String name() {
        return "Remove spring-cloud-dependencies from dependencyManagement";
    }
}
