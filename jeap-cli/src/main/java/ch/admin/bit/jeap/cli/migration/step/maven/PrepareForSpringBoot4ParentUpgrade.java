package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Prepares pom.xml files for the Spring Boot 4 parent upgrade.
 * <p>
 * The following changes are applied to all pom.xml files found recursively under the root directory:
 * <ul>
 *   <li>Replaces {@code com.github.tomakehurst:wiremock-jre8-standalone} with
 *       {@code org.wiremock:wiremock-standalone} (now managed by the new parent)</li>
 *   <li>Ensures dependencies that are no longer managed by the parent are centrally managed
 *       in the project's root {@code <dependencyManagement>} section:</li>
 *       {@code commons-compress}, {@code commons-io}, {@code commons-beanutils},
 *       {@code org.lz4:lz4-java}, {@code at.yawk.lz4:lz4-java}, {@code jose4j}</li>
 *   <li>Removes explicit {@code <version>} pins from dependency declarations once those
 *       dependencies are managed at project level</li>
 *   <li>Replaces {@code spring-boot-starter-aop} with {@code aspectj} (Spring Boot 4 renamed the artifact)</li>
 * </ul>
 */
@Slf4j
public class PrepareForSpringBoot4ParentUpgrade implements Step {

    private static final String POM_XML_FILE = "pom.xml";
    private static final Pattern MAVEN_CENTRAL_VERSION_PATTERN = Pattern.compile("\\\"v\\\":\\\"([^\\\"]+)\\\"");

    // The Spring Boot 4 alpha parent version is set first so that Maven can resolve
    // Spring Boot 4 managed dependencies during dependency management preparation.
    // The parent will be updated to the latest released version in the subsequent UpdateJeapParent step.
    private static final String SPRING_BOOT_4_ALPHA_PARENT_VERSION = "34.6.0-alpha-springboot4";

    // Matches the <parent> block that contains the jeap groupId, capturing the <version> element.
    private static final Pattern PARENT_VERSION_PATTERN = Pattern.compile(
            "(<parent>.*?<groupId>\\s*ch\\.admin\\.bit\\.jeap\\s*</groupId>.*?<version>\\s*)([^<]+)(\\s*</version>.*?</parent>)",
            Pattern.DOTALL
    );

    private static final List<DependencyCoordinate> DEPENDENCIES_TO_PROJECT_MANAGE = List.of(
            new DependencyCoordinate("org.apache.commons", "commons-compress"),
            new DependencyCoordinate("commons-io", "commons-io"),
            new DependencyCoordinate("commons-beanutils", "commons-beanutils"),
            new DependencyCoordinate("org.lz4", "lz4-java"),
            new DependencyCoordinate("at.yawk.lz4", "lz4-java"),
            new DependencyCoordinate("org.bitbucket.b_c", "jose4j")
    );

    private final Path rootDirectory;
    private final DependencyVersionResolver dependencyVersionResolver;

    public PrepareForSpringBoot4ParentUpgrade(Path rootDirectory) {
        this(rootDirectory, new MavenCentralVersionResolver());
    }

    PrepareForSpringBoot4ParentUpgrade(Path rootDirectory, DependencyVersionResolver dependencyVersionResolver) {
        this.rootDirectory = rootDirectory;
        this.dependencyVersionResolver = dependencyVersionResolver;
    }

    @Override
    public void execute() throws IOException {
        // First, pin the jEAP parent to the Spring Boot 4 alpha version so that
        // Spring Boot 4 managed dependencies are available for resolution below.
        setJeapParentVersion();

        List<Path> pomFiles = findPomFiles();
        Map<DependencyCoordinate, String> versionsToManage = resolveVersionsToManage(pomFiles);
        Set<DependencyCoordinate> projectManagedDependencies = ensureRootDependencyManagement(versionsToManage);

        for (Path pomPath : pomFiles) {
            updatePomFile(pomPath, projectManagedDependencies);
        }
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

    private Map<DependencyCoordinate, String> resolveVersionsToManage(List<Path> pomFiles) throws IOException {
        Map<DependencyCoordinate, String> versions = new LinkedHashMap<>();
        Path rootPom = rootDirectory.resolve(POM_XML_FILE);
        String rootPomContent = Files.exists(rootPom) ? Files.readString(rootPom, StandardCharsets.UTF_8) : "";

        for (DependencyCoordinate coordinate : DEPENDENCIES_TO_PROJECT_MANAGE) {
            String chosenVersion = chooseVersionForCoordinate(coordinate, pomFiles, rootPomContent);
            if (chosenVersion != null) {
                versions.put(coordinate, chosenVersion);
            } else {
                log.warn("No version found to project-manage dependency {}:{}", coordinate.groupId(), coordinate.artifactId());
            }
        }

        return versions;
    }

    private String chooseVersionForCoordinate(DependencyCoordinate coordinate, List<Path> pomFiles, String rootPomContent) throws IOException {
        Optional<String> versionFromMavenCentral = resolveLatestVersionFromMavenCentral(coordinate);
        if (versionFromMavenCentral.isPresent()) {
            return versionFromMavenCentral.get();
        }

        Optional<String> rootVersion = findVersionInPom(rootPomContent, coordinate);
        if (rootVersion.isPresent()) {
            return rootVersion.get();
        }

        String firstPropertyVersion = null;
        String firstLiteralVersion = null;
        for (Path pomPath : pomFiles) {
            String pomContent = Files.readString(pomPath, StandardCharsets.UTF_8);
            Optional<String> candidate = findVersionInPom(pomContent, coordinate);
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

    private Optional<String> resolveLatestVersionFromMavenCentral(DependencyCoordinate coordinate) {
        try {
            return dependencyVersionResolver.resolveLatestVersion(coordinate.groupId(), coordinate.artifactId());
        } catch (IOException | InterruptedException e) {
            log.warn("Could not resolve latest version from Maven Central for {}:{} - falling back to existing project version", coordinate.groupId(), coordinate.artifactId());
            log.debug("Maven Central lookup error for {}:{}", coordinate.groupId(), coordinate.artifactId(), e);
            return Optional.empty();
        }
    }

    private Set<DependencyCoordinate> ensureRootDependencyManagement(Map<DependencyCoordinate, String> versionsToManage) throws IOException {
        Path rootPom = rootDirectory.resolve(POM_XML_FILE);
        if (!Files.exists(rootPom)) {
            log.warn("No root pom.xml found at {}, skipping dependencyManagement update", rootPom);
            return Set.of();
        }

        String original = Files.readString(rootPom, StandardCharsets.UTF_8);
        String content = original;
        StringBuilder entriesToAdd = new StringBuilder();
        Set<DependencyCoordinate> managed = new java.util.LinkedHashSet<>();

        for (Map.Entry<DependencyCoordinate, String> entry : versionsToManage.entrySet()) {
            DependencyCoordinate coordinate = entry.getKey();
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
            log.info("Updated root pom.xml dependencyManagement for Spring Boot 4 parent upgrade");
        }
        return managed;
    }

    private void updatePomFile(Path pomPath, Set<DependencyCoordinate> projectManagedDependencies) throws IOException {
        String original = Files.readString(pomPath, StandardCharsets.UTF_8);
        String content = original;

        // 1) Replace wiremock-jre8-standalone (com.github.tomakehurst) with wiremock-standalone (org.wiremock)
        content = replaceWiremock(content);

        // 2) Remove explicit <version> from dependencies that are now project-managed in root pom.xml.
        for (DependencyCoordinate coordinate : projectManagedDependencies) {
            content = removeVersionFromDependency(content, coordinate.groupId(), coordinate.artifactId());
        }

        // 3) Replace spring-boot-starter-aop with aspectj (changed artifactId in Spring Boot 4)
        content = replaceAopArtifactId(content);

        if (!content.equals(original)) {
            Files.writeString(pomPath, content, StandardCharsets.UTF_8);
            log.info("Prepared {} for Spring Boot 4 parent upgrade", pomPath);
        }
    }

    /**
     * Replaces the old WireMock dependency (com.github.tomakehurst:wiremock-jre8-standalone)
     * with the new one (org.wiremock:wiremock-standalone).
     * The replacement preserves all other child elements (e.g. {@code <scope>}).
     */
    private String replaceWiremock(String content) {
        // Match a <dependency> block that contains the old groupId and old artifactId
        Pattern pattern = Pattern.compile(
                "(<dependency>\\s*)" +
                "<groupId>\\s*com\\.github\\.tomakehurst\\s*</groupId>(\\s*)" +
                "<artifactId>\\s*wiremock-jre8-standalone\\s*</artifactId>" +
                "(.*?)" +
                "(</dependency>)",
                Pattern.DOTALL);

        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean replaced = false;
        while (matcher.find()) {
            String leadingTag  = matcher.group(1);   // "<dependency>\n    "
            String betweenGidAid = matcher.group(2); // whitespace between groupId and artifactId
            String rest        = matcher.group(3);   // everything after artifactId (scope, etc.)
            String closingTag  = matcher.group(4);   // "</dependency>"

            String replacement = leadingTag +
                    "<groupId>org.wiremock</groupId>" + betweenGidAid +
                    "<artifactId>wiremock-standalone</artifactId>" +
                    rest +
                    closingTag;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            replaced = true;
            log.debug("Replaced wiremock-jre8-standalone with wiremock-standalone");
        }
        matcher.appendTail(sb);
        if (replaced) {
            return sb.toString();
        }
        return content;
    }

    /**
     * Removes the {@code <version>} element from a specific dependency identified by groupId and artifactId.
     * This is needed for dependencies that are no longer managed by the parent and whose version
     * override must be removed so that the project compiles without version conflicts.
     */
    private String removeVersionFromDependency(String content, String groupId, String artifactId) {
        // Match a <dependency> block containing the given groupId and artifactId (in any order)
        // then remove the <version>...</version> child element from it.
        Pattern depPattern = Pattern.compile(
                "(<dependency>)(.*?)(</dependency>)",
                Pattern.DOTALL);

        Matcher depMatcher = depPattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;

        while (depMatcher.find()) {
            String depBlock = depMatcher.group(0);
            boolean isRegularDependency = !isInsideDependencyManagement(content, depMatcher.start());
            if (isRegularDependency && containsGroupAndArtifact(depBlock, groupId, artifactId) && containsVersion(depBlock)) {
                String updated = removeVersionElement(depBlock);
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

    private boolean containsGroupAndArtifact(String depBlock, String groupId, String artifactId) {
        Pattern groupPattern = Pattern.compile("<groupId>\\s*" + Pattern.quote(groupId) + "\\s*</groupId>");
        Pattern artifactPattern = Pattern.compile("<artifactId>\\s*" + Pattern.quote(artifactId) + "\\s*</artifactId>");
        return groupPattern.matcher(depBlock).find() && artifactPattern.matcher(depBlock).find();
    }

    private boolean containsVersion(String depBlock) {
        return depBlock.contains("<version>");
    }

    private String removeVersionElement(String depBlock) {
        // Remove the <version>...</version> element including surrounding whitespace/newline
        return depBlock.replaceAll("\\s*<version>[^<]*</version>", "");
    }

    /**
     * Replaces the artifactId {@code spring-boot-starter-aop} with {@code spring-boot-starter-aspectj}
     * inside dependency blocks. Spring Boot 4 renamed this starter.
     */
    private String replaceAopArtifactId(String content) {
        String updated = content.replace(
                "<artifactId>spring-boot-starter-aop</artifactId>",
                "<artifactId>spring-boot-starter-aspectj</artifactId>");
        if (!updated.equals(content)) {
            log.debug("Replaced spring-boot-starter-aop with aspectj");
        }
        return updated;
    }

    @Override
    public String name() {
        return "Prepare pom.xml files for Spring Boot 4 parent upgrade";
    }

    private Optional<String> findVersionInPom(String pomContent, DependencyCoordinate coordinate) {
        Pattern dependencyPattern = Pattern.compile("(<dependency>)(.*?)(</dependency>)", Pattern.DOTALL);
        Matcher matcher = dependencyPattern.matcher(pomContent);
        while (matcher.find()) {
            String dependencyBlock = matcher.group(0);
            if (!containsGroupAndArtifact(dependencyBlock, coordinate.groupId(), coordinate.artifactId())) {
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

    private boolean isManagedInDependencyManagement(String pomContent, DependencyCoordinate coordinate) {
        Matcher matcher = Pattern.compile("(<dependencyManagement>\\s*<dependencies>)(.*?)(</dependencies>\\s*</dependencyManagement>)", Pattern.DOTALL)
                .matcher(pomContent);
        if (!matcher.find()) {
            return false;
        }
        String dependencyManagementContent = matcher.group(2);
        return containsGroupAndArtifact(dependencyManagementContent, coordinate.groupId(), coordinate.artifactId());
    }

    private String buildManagedDependencyEntry(DependencyCoordinate coordinate, String version) {
        return "\n" +
                "            <!-- TODO(jeap-cli): Verify whether this dependency still needs explicit project-level management and whether version " + version + " is appropriate for your project. This dependency was previously managed by Spring Boot or the jeap-parent, which is why it is now in dependency management. -->\n" +
                "            <dependency>\n" +
                "                <groupId>" + coordinate.groupId() + "</groupId>\n" +
                "                <artifactId>" + coordinate.artifactId() + "</artifactId>\n" +
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

    private void setJeapParentVersion() throws IOException {
        Path rootPom = rootDirectory.resolve(POM_XML_FILE);
        if (!Files.exists(rootPom)) {
            log.warn("No root pom.xml found at {}, skipping parent version update", rootPom);
            return;
        }

        String original = Files.readString(rootPom, StandardCharsets.UTF_8);
        Matcher matcher = PARENT_VERSION_PATTERN.matcher(original);

        if (!matcher.find()) {
            log.warn("Could not find a ch.admin.bit.jeap <parent> block in {}, skipping parent version update", rootPom);
            return;
        }

        String currentVersion = matcher.group(2).trim();
        if (currentVersion.equals(SPRING_BOOT_4_ALPHA_PARENT_VERSION)) {
            log.info("Parent version is already {}, nothing to do", SPRING_BOOT_4_ALPHA_PARENT_VERSION);
            return;
        }

        String updated = matcher.replaceFirst(
                Matcher.quoteReplacement(matcher.group(1) + SPRING_BOOT_4_ALPHA_PARENT_VERSION + matcher.group(3)));
        Files.writeString(rootPom, updated, StandardCharsets.UTF_8);
        log.info("Set jeap parent version from {} to {} in {}", currentVersion, SPRING_BOOT_4_ALPHA_PARENT_VERSION, rootPom);
    }

    private record DependencyCoordinate(String groupId, String artifactId) {
        private DependencyCoordinate {
            Objects.requireNonNull(groupId);
            Objects.requireNonNull(artifactId);
        }
    }

    @FunctionalInterface
    interface DependencyVersionResolver {
        Optional<String> resolveLatestVersion(String groupId, String artifactId) throws IOException, InterruptedException;
    }

    private static class MavenCentralVersionResolver implements DependencyVersionResolver {
        private static final String MAVEN_CENTRAL_URL_TEMPLATE =
                "https://search.maven.org/solrsearch/select?q=g:%5C\"%s%5C\"+AND+a:%5C\"%s%5C\"&rows=20&core=gav&wt=json&sort=timestamp+desc";

        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        @Override
        public Optional<String> resolveLatestVersion(String groupId, String artifactId) throws IOException, InterruptedException {
            String encodedGroupId = URLEncoder.encode(groupId, StandardCharsets.UTF_8);
            String encodedArtifactId = URLEncoder.encode(artifactId, StandardCharsets.UTF_8);
            String url = MAVEN_CENTRAL_URL_TEMPLATE.formatted(encodedGroupId, encodedArtifactId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return extractLatestStableVersion(response.body());
        }

        private Optional<String> extractLatestStableVersion(String responseBody) {
            Matcher matcher = MAVEN_CENTRAL_VERSION_PATTERN.matcher(responseBody);
            while (matcher.find()) {
                String candidate = matcher.group(1);
                if (isStableVersion(candidate)) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
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
    }
}

