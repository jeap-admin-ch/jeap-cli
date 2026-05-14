package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sets the version of a ch.admin.bit.jeap {@code <parent>} block in the root pom.xml
 * to a given target version. When constructed with a map, the target version is looked
 * up by the parent {@code <artifactId>}; artifacts not present in the map are skipped.
 * When constructed with a single version string, that version is applied to any
 * ch.admin.bit.jeap parent regardless of its artifact ID.
 */
@Slf4j
class SetJeapParentVersion implements Step {

    private static final String POM_XML_FILE = "pom.xml";
    private static final Pattern PARENT_VERSION_PATTERN = Pattern.compile(
            "(<parent>.*?<groupId>\\s*ch\\.admin\\.bit\\.jeap\\s*</groupId>.*?<version>\\s*)([^<]+)(\\s*</version>.*?</parent>)",
            Pattern.DOTALL
    );
    private static final Pattern PARENT_ARTIFACT_ID_PATTERN = Pattern.compile(
            "<parent>.*?<artifactId>\\s*([^<]+)\\s*</artifactId>.*?</parent>",
            Pattern.DOTALL
    );

    private final Path rootDirectory;
    private final Map<String, String> artifactIdToTargetVersion;
    private final String defaultTargetVersion;

    /** Applies {@code targetVersion} to any ch.admin.bit.jeap parent, regardless of artifact ID. */
    SetJeapParentVersion(Path rootDirectory, String targetVersion) {
        this.rootDirectory = rootDirectory;
        this.artifactIdToTargetVersion = null;
        this.defaultTargetVersion = targetVersion;
    }

    /** Looks up the target version by parent {@code <artifactId>}; skips unknown artifacts. */
    SetJeapParentVersion(Path rootDirectory, Map<String, String> artifactIdToTargetVersion) {
        this.rootDirectory = rootDirectory;
        this.artifactIdToTargetVersion = Map.copyOf(artifactIdToTargetVersion);
        this.defaultTargetVersion = null;
    }

    @Override
    public void execute() throws IOException {
        Path rootPom = rootDirectory.resolve(POM_XML_FILE);
        if (!Files.exists(rootPom)) {
            log.warn("No root pom.xml found at {}, skipping parent version update", rootPom);
            return;
        }

        String original = Files.readString(rootPom, StandardCharsets.UTF_8);
        Matcher versionMatcher = PARENT_VERSION_PATTERN.matcher(original);

        if (!versionMatcher.find()) {
            log.warn("Could not find a ch.admin.bit.jeap <parent> block in {}, skipping parent version update", rootPom);
            return;
        }

        String artifactId = extractParentArtifactId(original);
        String targetVersion = resolveTargetVersion(artifactId);
        if (targetVersion == null) {
            log.warn("No target version configured for parent artifact '{}' in {}, skipping", artifactId, rootPom);
            return;
        }

        String currentVersion = versionMatcher.group(2).trim();
        if (currentVersion.equals(targetVersion)) {
            log.info("Parent version is already {}, nothing to do", targetVersion);
            return;
        }

        String updated = versionMatcher.replaceFirst(
                Matcher.quoteReplacement(versionMatcher.group(1) + targetVersion + versionMatcher.group(3)));
        Files.writeString(rootPom, updated, StandardCharsets.UTF_8);
        log.info("Set jeap parent version from {} to {} in {}", currentVersion, targetVersion, rootPom);
    }

    private String extractParentArtifactId(String pomContent) {
        Matcher m = PARENT_ARTIFACT_ID_PATTERN.matcher(pomContent);
        return m.find() ? m.group(1).trim() : "";
    }

    private String resolveTargetVersion(String artifactId) {
        return artifactIdToTargetVersion != null
                ? artifactIdToTargetVersion.get(artifactId)
                : defaultTargetVersion;
    }
}
