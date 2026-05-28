package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Applies code formatting to files that were modified by the migration.
 * <p>
 * If the project uses {@code com.diffplug.spotless:spotless-maven-plugin}, runs
 * {@code spotless:apply} to fix import-ordering and other format violations introduced
 * by OpenRewrite (e.g. alphabetical reordering of imports).
 * </p>
 * <p>
 * If the project uses {@code com.cosium.code:git-code-format-maven-plugin}, runs
 * {@code git-code-format:format-code}. The plugin limits formatting to git-modified
 * files automatically via {@code git diff}, so only migration-touched files are reformatted.
 * </p>
 * If neither plugin is found in any pom.xml, this step is skipped silently.
 */
@Slf4j
public class RunCodeFormat implements Step {

    private static final String SPOTLESS_PLUGIN = "spotless-maven-plugin";
    private static final String GIT_CODE_FORMAT_PLUGIN = "git-code-format-maven-plugin";

    private final Path rootDirectory;
    private final ProcessExecutor processExecutor;

    public RunCodeFormat(Path rootDirectory, ProcessExecutor processExecutor) {
        this.rootDirectory = rootDirectory;
        this.processExecutor = processExecutor;
    }

    @Override
    public void execute() throws Exception {
        boolean spotless = isSpotlessPluginPresent();
        boolean gitCodeFormat = isGitCodeFormatPluginPresent();

        if (!spotless && !gitCodeFormat) {
            log.info("No code format plugin found in pom.xml files — skipping code format step");
            return;
        }
        if (spotless) {
            log.info("Running spotless:apply to fix formatting violations introduced by OpenRewrite");
            for (Path moduleDirectory : findPomDirectoriesWithPlugin(SPOTLESS_PLUGIN)) {
                new RunMaven(moduleDirectory, processExecutor, "spotless:apply").execute();
            }
        }
        if (gitCodeFormat) {
            log.info("Running git-code-format:format-code on git-modified files");
            for (Path moduleDirectory : findPomDirectoriesWithPlugin(GIT_CODE_FORMAT_PLUGIN)) {
                new RunMaven(moduleDirectory, processExecutor, "com.cosium.code:git-code-format-maven-plugin:format-code").execute();
            }
        }
    }

    /**
     * Returns {@code true} if any pom.xml under the root directory references
     * {@code spotless-maven-plugin}.
     */
    boolean isSpotlessPluginPresent() throws IOException {
        return isPomPluginPresent(SPOTLESS_PLUGIN);
    }

    /**
     * Returns {@code true} if any pom.xml under the root directory references
     * {@code git-code-format-maven-plugin}.
     */
    boolean isGitCodeFormatPluginPresent() throws IOException {
        return isPomPluginPresent(GIT_CODE_FORMAT_PLUGIN);
    }

    private boolean isPomPluginPresent(String pluginArtifactId) throws IOException {
        return !findPomDirectoriesWithPlugin(pluginArtifactId).isEmpty();
    }

    private Set<Path> findPomDirectoriesWithPlugin(String pluginArtifactId) throws IOException {
        Set<Path> directories = new LinkedHashSet<>();
        if (!Files.exists(rootDirectory)) {
            return directories;
        }
        try (Stream<Path> stream = Files.find(
                rootDirectory,
                Integer.MAX_VALUE,
                (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().equals("pom.xml"))) {
            stream.forEach(pom -> {
                try {
                    if (Files.readString(pom, StandardCharsets.UTF_8).contains(pluginArtifactId)) {
                        directories.add(pom.getParent());
                    }
                } catch (IOException e) {
                    // ignore unreadable pom and continue scanning
                }
            });
        }
        return directories;
    }

    @Override
    public String name() {
        return "Run code formatters (spotless + git-code-format)";
    }
}
