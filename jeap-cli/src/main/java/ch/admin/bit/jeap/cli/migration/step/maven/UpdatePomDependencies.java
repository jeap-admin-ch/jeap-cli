package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Applies a list of {@link DependencyReplacement}s to all pom.xml files under a root directory,
 * and removes {@code <version>} tags from dependencies that are now project-managed.
 */
@Slf4j
class UpdatePomDependencies implements Step {

    private static final String POM_XML_FILE = "pom.xml";

    private final Path rootDirectory;
    private final Supplier<Set<String>> projectManagedDependenciesSupplier;
    private final List<DependencyReplacement> dependencyReplacements;

    UpdatePomDependencies(Path rootDirectory,
                          Supplier<Set<String>> projectManagedDependenciesSupplier,
                          List<DependencyReplacement> dependencyReplacements) {
        this.rootDirectory = rootDirectory;
        this.projectManagedDependenciesSupplier = projectManagedDependenciesSupplier;
        this.dependencyReplacements = dependencyReplacements;
    }

    @Override
    public void execute() throws IOException {
        Set<String> projectManagedDependencies = projectManagedDependenciesSupplier.get();
        for (Path pomPath : findPomFiles()) {
            updatePomFile(pomPath, projectManagedDependencies);
        }
    }

    private List<Path> findPomFiles() throws IOException {
        if (!Files.exists(rootDirectory)) {
            log.debug("Root directory {} does not exist, skipping pom.xml update", rootDirectory);
            return List.of();
        }
        try (Stream<Path> stream = Files.find(
                rootDirectory,
                Integer.MAX_VALUE,
                (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().equals(POM_XML_FILE))) {
            return stream.toList();
        }
    }

    private void updatePomFile(Path pomPath, Set<String> projectManagedDependencies) throws IOException {
        String original = Files.readString(pomPath, StandardCharsets.UTF_8);
        String content = original;

        for (DependencyReplacement replacement : dependencyReplacements) {
            content = applyReplacement(content, replacement);
        }

        for (String coordinate : projectManagedDependencies) {
            String[] parts = splitCoordinate(coordinate);
            content = removeVersionFromDependency(content, parts[0], parts[1]);
        }

        if (!content.equals(original)) {
            Files.writeString(pomPath, content, StandardCharsets.UTF_8);
            log.info("Updated pom.xml dependency declarations in {}", pomPath);
        }
    }

    private String applyReplacement(String content, DependencyReplacement replacement) {
        if (replacement.fromGroupId() == null) {
            return applyArtifactOnlyReplacement(content, replacement);
        }
        return applyGroupAndArtifactReplacement(content, replacement);
    }

    /**
     * Replaces the artifactId within {@code <dependency>} blocks, scoped by the artifactId value only
     * (i.e. any groupId matches). Uses the same dependency-block pattern as the group+artifact variant
     * to avoid accidentally renaming {@code <artifactId>} entries in {@code <parent>}, {@code <plugin>},
     * or the project's own declaration.
     */
    private String applyArtifactOnlyReplacement(String content, DependencyReplacement replacement) {
        Pattern pattern = Pattern.compile(
                "(<dependency>.*?<artifactId>\\s*)" +
                        Pattern.quote(replacement.fromArtifactId()) +
                        "(\\s*</artifactId>.*?</dependency>)",
                Pattern.DOTALL);

        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean replaced = false;

        while (matcher.find()) {
            String rep = matcher.group(1) + replacement.toArtifactId() + matcher.group(2);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(rep));
            replaced = true;
            log.debug("Renamed artifactId {} to {}", replacement.fromArtifactId(), replacement.toArtifactId());
        }
        matcher.appendTail(sb);
        return replaced ? sb.toString() : content;
    }

    /**
     * Replaces both groupId and artifactId within a {@code <dependency>} block, preserving surrounding whitespace.
     */
    private String applyGroupAndArtifactReplacement(String content, DependencyReplacement replacement) {
        Pattern pattern = Pattern.compile(
                "(<dependency>\\s*)" +
                        "<groupId>\\s*" + Pattern.quote(replacement.fromGroupId()) + "\\s*</groupId>(\\s*)" +
                        "<artifactId>\\s*" + Pattern.quote(replacement.fromArtifactId()) + "\\s*</artifactId>" +
                        "(.*?)(</dependency>)",
                Pattern.DOTALL);

        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean replaced = false;
        String toGroupId = replacement.toGroupId() != null ? replacement.toGroupId() : replacement.fromGroupId();

        while (matcher.find()) {
            String rep = matcher.group(1) +
                    "<groupId>" + toGroupId + "</groupId>" + matcher.group(2) +
                    "<artifactId>" + replacement.toArtifactId() + "</artifactId>" +
                    matcher.group(3) + matcher.group(4);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(rep));
            replaced = true;
            log.debug("Replaced dependency {}:{} with {}:{}", replacement.fromGroupId(), replacement.fromArtifactId(),
                    toGroupId, replacement.toArtifactId());
        }
        matcher.appendTail(sb);
        return replaced ? sb.toString() : content;
    }

    private String removeVersionFromDependency(String content, String groupId, String artifactId) {
        Pattern depPattern = Pattern.compile("(<dependency>)(.*?)(</dependency>)", Pattern.DOTALL);
        Matcher depMatcher = depPattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;

        while (depMatcher.find()) {
            String depBlock = depMatcher.group(0);
            int matchStart = depMatcher.start();
            boolean isProjectDependency = !isInsideDependencyManagement(content, matchStart)
                    && !isInsidePluginDependencies(content, matchStart);
            if (isProjectDependency && containsGroupAndArtifact(depBlock, groupId, artifactId) && depBlock.contains("<version>")) {
                String updated = depBlock.replaceAll("\\s*<version>[^<]*</version>", "");
                depMatcher.appendReplacement(sb, Matcher.quoteReplacement(updated));
                changed = true;
                log.debug("Removed <version> from dependency {}:{}", groupId, artifactId);
            } else {
                depMatcher.appendReplacement(sb, Matcher.quoteReplacement(depBlock));
            }
        }
        depMatcher.appendTail(sb);
        return changed ? sb.toString() : content;
    }

    private boolean isInsideDependencyManagement(String pomContent, int dependencyStartIndex) {
        int openIndex = pomContent.lastIndexOf("<dependencyManagement>", dependencyStartIndex);
        int closeIndex = pomContent.lastIndexOf("</dependencyManagement>", dependencyStartIndex);
        return openIndex >= 0 && closeIndex < openIndex;
    }

    private boolean isInsidePluginDependencies(String pomContent, int dependencyStartIndex) {
        int openIndex = pomContent.lastIndexOf("<plugin>", dependencyStartIndex);
        int closeIndex = pomContent.lastIndexOf("</plugin>", dependencyStartIndex);
        return openIndex >= 0 && closeIndex < openIndex;
    }

    private boolean containsGroupAndArtifact(String depBlock, String groupId, String artifactId) {
        Pattern groupPattern = Pattern.compile("<groupId>\\s*" + Pattern.quote(groupId) + "\\s*</groupId>");
        Pattern artifactPattern = Pattern.compile("<artifactId>\\s*" + Pattern.quote(artifactId) + "\\s*</artifactId>");
        return groupPattern.matcher(depBlock).find() && artifactPattern.matcher(depBlock).find();
    }

    private String[] splitCoordinate(String coordinate) {
        int separator = coordinate.indexOf(':');
        return new String[]{coordinate.substring(0, separator), coordinate.substring(separator + 1)};
    }
}
