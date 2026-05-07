package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.process.ProcessExecutionResult;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import ch.admin.bit.jeap.cli.process.SystemProcessExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans all pom.xml files for locally versioned dependencies from a given list and ensures that
 * those dependencies are declared inside the root {@code <dependencyManagement>} block.
 * Dependencies already managed at the root level are left untouched.
 * <p>
 * After execution, {@link #projectManagedDependencies()} returns all coordinates (groupId:artifactId)
 * that are now project-managed, so subsequent steps can strip redundant {@code <version>} tags.
 */
@Slf4j
class EnsureProjectDependencyManagement implements Step {

    private static final String POM_XML_FILE = "pom.xml";

    private final Path rootDirectory;
    private final List<String> dependenciesToManage;
    private final DependencyVersionResolver dependencyVersionResolver;
    private Set<String> projectManagedDependencies = Set.of();

    EnsureProjectDependencyManagement(Path rootDirectory,
                                      List<String> dependenciesToManage,
                                      DependencyVersionResolver dependencyVersionResolver) {
        this.rootDirectory = rootDirectory;
        this.dependenciesToManage = dependenciesToManage;
        this.dependencyVersionResolver = dependencyVersionResolver;
    }

    @Override
    public void execute() throws IOException {
        List<Path> pomFiles = findPomFiles();
        Map<String, String> versionsToManage = resolveVersionsToManage(pomFiles);
        this.projectManagedDependencies = ensureRootDependencyManagement(versionsToManage);
    }

    Set<String> projectManagedDependencies() {
        return projectManagedDependencies;
    }

    private List<Path> findPomFiles() throws IOException {
        if (!Files.exists(rootDirectory)) {
            log.debug("Root directory {} does not exist, skipping pom.xml preparation", rootDirectory);
            return List.of();
        }
        try (Stream<Path> stream = Files.find(
                rootDirectory,
                Integer.MAX_VALUE,
                (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().equals(POM_XML_FILE))) {
            return stream.toList();
        }
    }

    private Map<String, String> resolveVersionsToManage(List<Path> pomFiles) throws IOException {
        Map<String, String> versions = new LinkedHashMap<>();
        Path rootPom = rootDirectory.resolve(POM_XML_FILE);
        String rootPomContent = Files.exists(rootPom) ? Files.readString(rootPom, StandardCharsets.UTF_8) : "";

        for (String coordinate : dependenciesToManage) {
            String chosenVersion = chooseVersionForCoordinate(coordinate, pomFiles, rootPomContent);
            if (chosenVersion != null) {
                versions.put(coordinate, chosenVersion);
            } else {
                log.warn("No version found to project-manage dependency {}", coordinate);
            }
        }

        return versions;
    }

    private String chooseVersionForCoordinate(String coordinate, List<Path> pomFiles, String rootPomContent) throws IOException {
        String[] parts = splitCoordinate(coordinate);
        Optional<String> versionFromMavenCentral = resolveLatestVersionFromMavenCentral(parts[0], parts[1]);
        if (versionFromMavenCentral.isPresent()) {
            return versionFromMavenCentral.get();
        }

        Optional<String> rootVersion = findVersionInPom(rootPomContent, parts[0], parts[1]);
        if (rootVersion.isPresent()) {
            return rootVersion.get();
        }

        String firstPropertyVersion = null;
        String firstLiteralVersion = null;
        for (Path pomPath : pomFiles) {
            String pomContent = Files.readString(pomPath, StandardCharsets.UTF_8);
            Optional<String> candidate = findVersionInPom(pomContent, parts[0], parts[1]);
            if (candidate.isPresent()) {
                String version = candidate.get();
                if (isPropertyExpression(version)) {
                    if (firstPropertyVersion == null) {
                        firstPropertyVersion = version;
                    }
                } else if (firstLiteralVersion == null) {
                    firstLiteralVersion = version;
                }
            }
        }
        return firstLiteralVersion != null ? firstLiteralVersion : firstPropertyVersion;
    }

    private Optional<String> resolveLatestVersionFromMavenCentral(String groupId, String artifactId) {
        try {
            return dependencyVersionResolver.resolveLatestVersion(groupId, artifactId);
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            log.warn("Could not resolve latest version from Maven Central for {}:{} - falling back to existing project version", groupId, artifactId);
            log.debug("Maven Central lookup error for {}:{}", groupId, artifactId, e);
            return Optional.empty();
        }
    }

    private Set<String> ensureRootDependencyManagement(Map<String, String> versionsToManage) throws IOException {
        Path rootPom = rootDirectory.resolve(POM_XML_FILE);
        if (!Files.exists(rootPom)) {
            log.warn("No root pom.xml found at {}, skipping dependencyManagement update", rootPom);
            return Set.of();
        }

        String original = Files.readString(rootPom, StandardCharsets.UTF_8);
        String content = original;
        StringBuilder entriesToAdd = new StringBuilder();
        Set<String> managed = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : versionsToManage.entrySet()) {
            String coordinate = entry.getKey();
            String version = entry.getValue();
            boolean hasUsableVersion = version != null && !version.isBlank();
            boolean alreadyManaged = isManagedInDependencyManagement(content, coordinate);

            if (alreadyManaged) {
                managed.add(coordinate);
            } else if (hasUsableVersion) {
                entriesToAdd.append(buildManagedDependencyEntry(coordinate, version));
                managed.add(coordinate);
            }
        }

        if (!entriesToAdd.isEmpty()) {
            content = insertIntoDependencyManagement(content, entriesToAdd.toString());
        }

        if (!content.equals(original)) {
            Files.writeString(rootPom, content, StandardCharsets.UTF_8);
            log.info("Updated root pom.xml dependencyManagement");
        }
        return managed;
    }

    private Optional<String> findVersionInPom(String pomContent, String groupId, String artifactId) {
        Pattern dependencyPattern = Pattern.compile("(<dependency>)(.*?)(</dependency>)", Pattern.DOTALL);
        Matcher matcher = dependencyPattern.matcher(pomContent);
        while (matcher.find()) {
            String dependencyBlock = matcher.group(0);
            if (!containsGroupAndArtifact(dependencyBlock, groupId, artifactId)) {
                continue;
            }
            Matcher versionMatcher = Pattern.compile("<version>\\s*([^<]+)\\s*</version>").matcher(dependencyBlock);
            if (versionMatcher.find()) {
                return Optional.of(versionMatcher.group(1).trim());
            }
        }
        return Optional.empty();
    }

    private boolean isPropertyExpression(String version) {
        return version.startsWith("${") && version.endsWith("}");
    }

    private boolean isManagedInDependencyManagement(String pomContent, String coordinate) {
        Matcher matcher = Pattern.compile("(<dependencyManagement>\\s*<dependencies>)(.*?)(</dependencies>\\s*</dependencyManagement>)", Pattern.DOTALL)
                .matcher(pomContent);
        if (!matcher.find()) {
            return false;
        }
        String dependencyManagementContent = matcher.group(2);
        String[] parts = splitCoordinate(coordinate);
        return containsGroupAndArtifact(dependencyManagementContent, parts[0], parts[1]);
    }

    private String buildManagedDependencyEntry(String coordinate, String version) {
        String[] parts = splitCoordinate(coordinate);
        return "\n" +
                "            <!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version " + version + " is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. -->\n" +
                "            <dependency>\n" +
                "                <groupId>" + parts[0] + "</groupId>\n" +
                "                <artifactId>" + parts[1] + "</artifactId>\n" +
                "                <version>" + version + "</version>\n" +
                "            </dependency>";
    }

    private String insertIntoDependencyManagement(String pomContent, String dependencyEntries) {
        Matcher existingDependencyManagement = Pattern.compile(
                "(<dependencyManagement>\\s*<dependencies>)(.*?)(</dependencies>\\s*</dependencyManagement>)",
                Pattern.DOTALL
        ).matcher(pomContent);
        if (existingDependencyManagement.find()) {
            String replacement = existingDependencyManagement.group(1) +
                    existingDependencyManagement.group(2) +
                    dependencyEntries + "\n        " +
                    existingDependencyManagement.group(3);
            return existingDependencyManagement.replaceFirst(Matcher.quoteReplacement(replacement));
        }

        String dependencyManagementBlock = "\n    <dependencyManagement>\n" +
                "        <dependencies>" + dependencyEntries + "\n" +
                "        </dependencies>\n" +
                "    </dependencyManagement>\n";

        int insertIndex = findBestDependencyManagementInsertPosition(pomContent);
        if (insertIndex < 0) {
            return pomContent;
        }
        return pomContent.substring(0, insertIndex) + dependencyManagementBlock + pomContent.substring(insertIndex);
    }

    private int findBestDependencyManagementInsertPosition(String pomContent) {
        int dependenciesIndex = pomContent.indexOf("<dependencies>");
        if (dependenciesIndex >= 0) {
            return dependenciesIndex;
        }
        int buildIndex = pomContent.indexOf("<build>");
        if (buildIndex >= 0) {
            return buildIndex;
        }
        return pomContent.indexOf("</project>");
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

    @FunctionalInterface
    interface DependencyVersionResolver {
        Optional<String> resolveLatestVersion(String groupId, String artifactId) throws IOException, InterruptedException;
    }

    static class MavenCentralVersionResolver implements DependencyVersionResolver {
        private static final Pattern VERSION_PATTERN = Pattern.compile("<version>\\s*([^<]+)\\s*</version>");
        private static final String VERSIONS_PLUGIN_GOAL =
                "org.codehaus.mojo:versions-maven-plugin:2.18.0:use-latest-releases";

        private final ProcessExecutor processExecutor;
        private final String mavenCommand;

        MavenCentralVersionResolver() {
            this(Path.of("."), new SystemProcessExecutor());
        }

        MavenCentralVersionResolver(Path rootDirectory, ProcessExecutor processExecutor) {
            this.processExecutor = processExecutor;
            Path mvnw = rootDirectory.resolve("mvnw");
            this.mavenCommand = Files.exists(mvnw) && Files.isExecutable(mvnw) ? mvnw.toAbsolutePath().toString() : "mvn";
        }

        @Override
        public Optional<String> resolveLatestVersion(String groupId, String artifactId) throws IOException, InterruptedException {
            Path tempDir = Files.createTempDirectory("jeap-version-resolver-");
            Path tempPom = tempDir.resolve(POM_XML_FILE);
            try {
                Files.writeString(tempPom, temporaryPom(groupId, artifactId), StandardCharsets.UTF_8);

                List<String> command = List.of(
                        mavenCommand,
                        "-f", tempPom.toString(),
                        "-q",
                        "-DgenerateBackupPoms=false",
                        "-DprocessDependencyManagement=false",
                        "-DallowSnapshots=false",
                        "-Dincludes=" + groupId + ":" + artifactId,
                        VERSIONS_PLUGIN_GOAL
                );

                ProcessExecutionResult result = processExecutor.executeAndCapture(command, tempDir);
                if (result.exitCode() != 0) {
                    return Optional.empty();
                }

                String updatedPom = Files.readString(tempPom, StandardCharsets.UTF_8);
                return extractResolvedVersion(updatedPom)
                        .filter(this::isStableVersion);
            } finally {
                deleteDirectoryQuietly(tempDir);
            }
        }

        private String temporaryPom(String groupId, String artifactId) {
            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>ch.admin.bit.jeap.cli</groupId>
                        <artifactId>version-resolver</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>%s</groupId>
                                <artifactId>%s</artifactId>
                                <version>0.0.0</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(groupId, artifactId);
        }

        private Optional<String> extractResolvedVersion(String pomContent) {
            Matcher matcher = VERSION_PATTERN.matcher(pomContent);
            if (!matcher.find()) {
                return Optional.empty();
            }
            if (!matcher.find()) {
                return Optional.empty();
            }
            return Optional.of(matcher.group(1).trim());
        }

        private boolean isStableVersion(String version) {
            String lowered = version.toLowerCase();
            return !lowered.contains("snapshot") &&
                    !lowered.contains("alpha") &&
                    !lowered.contains("beta") &&
                    !lowered.contains("rc") &&
                    !lowered.contains("milestone") &&
                    !lowered.matches(".*\\bm\\d+.*");
        }

        private void deleteDirectoryQuietly(Path directory) {
            try (Stream<Path> stream = Files.walk(directory)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                                // Best-effort cleanup only.
                            }
                        });
            } catch (IOException ignored) {
                // Best-effort cleanup only.
            }
        }
    }
}
