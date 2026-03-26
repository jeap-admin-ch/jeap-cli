package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;

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
public class UpdateJeapParent implements Step {

    // Excludes versions with a dash-separated qualifier starting with a letter,
    // e.g. "1.2.0-alpha-springboot4", "1.2.0-RC1", while allowing numeric-only
    // suffixes like "5.14.0-1".
    static final String IGNORE_QUALIFIED_VERSIONS = ".*-[a-zA-Z].*";

    private final RunMaven runMaven;

    public UpdateJeapParent(Path workingDirectory, ProcessExecutor processExecutor) {
        this(workingDirectory, processExecutor, false);
    }

    public UpdateJeapParent(Path workingDirectory, ProcessExecutor processExecutor, boolean includeQualifiedVersions) {
        List<String> args = new ArrayList<>();
        args.add(MavenPlugin.VERSIONS.goal("update-parent"));
        args.add("-Dincludes=ch.admin.bit.jeap");
        args.add("-DgenerateBackupPoms=false");
        if (!includeQualifiedVersions) {
            args.add("-Dversions.ignoredVersions=" + IGNORE_QUALIFIED_VERSIONS);
        }
        this.runMaven = new RunMaven(workingDirectory, processExecutor, args.toArray(String[]::new));
    }

    @Override
    public void execute() throws Exception {
        runMaven.execute();
    }
}
