package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates the jEAP parent POM to the latest version using the Maven versions plugin.
 * <p>
 * By default, versions with a dash-separated qualifier starting with a letter are excluded
 * (e.g. "1.2.0-alpha-springboot4", "1.2.0-RC1"), while numeric-only suffixes like "5.14.0-1"
 * are allowed. Use {@code includeQualifiedVersions=true} to include all versions.
 */
@Slf4j
public class UpdateJeapParent implements Step {

    // Excludes versions with a dash-separated qualifier starting with a letter,
    // e.g. "1.2.0-alpha-springboot4", "1.2.0-RC1", while allowing numeric-only
    // suffixes like "5.14.0-1".
    static final String IGNORE_QUALIFIED_VERSIONS = ".*-[a-zA-Z].*";

    private final RunMaven runMaven;
    private final Path pomPath;
    private final boolean fallbackOnFailure;

    public UpdateJeapParent(Path workingDirectory, ProcessExecutor processExecutor) {
        this(workingDirectory, processExecutor, false);
    }

    public UpdateJeapParent(Path workingDirectory, ProcessExecutor processExecutor, boolean includeQualifiedVersions) {
        this(workingDirectory, processExecutor, includeQualifiedVersions, false);
    }

    /** Keeps the current root POM unchanged if the latest stable parent cannot be resolved. */
    public static UpdateJeapParent latestStableWithFallback(Path workingDirectory, ProcessExecutor processExecutor) {
        return new UpdateJeapParent(workingDirectory, processExecutor, false, true);
    }

    private UpdateJeapParent(Path workingDirectory, ProcessExecutor processExecutor,
                             boolean includeQualifiedVersions, boolean fallbackOnFailure) {
        List<String> args = new ArrayList<>();
        args.add(MavenPlugin.VERSIONS.goal("update-parent"));
        args.add("-Dincludes=ch.admin.bit.jeap");
        args.add("-DgenerateBackupPoms=false");
        if (!includeQualifiedVersions) {
            args.add("-Dversions.ignoredVersions=" + IGNORE_QUALIFIED_VERSIONS);
        }
        this.runMaven = new RunMaven(workingDirectory, processExecutor, args.toArray(String[]::new));
        this.pomPath = workingDirectory.resolve("pom.xml");
        this.fallbackOnFailure = fallbackOnFailure;
    }

    @Override
    public void execute() throws Exception {
        String fallbackPom = fallbackOnFailure && Files.isRegularFile(pomPath)
                ? Files.readString(pomPath, StandardCharsets.UTF_8)
                : null;
        try {
            runMaven.execute();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            restoreFallbackPom(fallbackPom);
            throw e;
        } catch (IOException e) {
            if (!fallbackOnFailure) {
                throw e;
            }
            restoreFallbackPom(fallbackPom);
            log.warn("Could not resolve the latest stable jEAP parent; keeping the current parent version: {}",
                    e.getMessage());
        }
    }

    private void restoreFallbackPom(String fallbackPom) throws IOException {
        if (fallbackPom != null) {
            Files.writeString(pomPath, fallbackPom, StandardCharsets.UTF_8);
        }
    }
}
