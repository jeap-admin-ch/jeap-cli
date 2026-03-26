package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates jEAP dependency versions to the latest releases using the Maven versions plugin.
 * Only updates dependencies with an explicit version in the POM - dependencies whose version
 * is managed by a parent POM (via dependencyManagement) are naturally skipped.
 * <p>
 * By default, versions with a dash-separated qualifier starting with a letter are excluded
 * (e.g. "1.2.0-alpha-springboot4", "1.2.0-RC1"). Use {@code includeQualifiedVersions=true}
 * to include all versions.
 */
public class UpdateJeapDependencies implements Step {

    private final RunMaven runMaven;

    public UpdateJeapDependencies(Path workingDirectory, ProcessExecutor processExecutor) {
        this(workingDirectory, processExecutor, false);
    }

    public UpdateJeapDependencies(Path workingDirectory, ProcessExecutor processExecutor, boolean includeQualifiedVersions) {
        List<String> args = new ArrayList<>();
        args.add(MavenPlugin.VERSIONS.goal("use-latest-releases"));
        args.add("-Dincludes=ch.admin.bit.jeap");
        args.add("-DgenerateBackupPoms=false");
        if (!includeQualifiedVersions) {
            args.add("-Dversions.ignoredVersions=" + UpdateJeapParent.IGNORE_QUALIFIED_VERSIONS);
        }
        this.runMaven = new RunMaven(workingDirectory, processExecutor, args.toArray(String[]::new));
    }

    @Override
    public void execute() throws Exception {
        runMaven.execute();
    }
}
