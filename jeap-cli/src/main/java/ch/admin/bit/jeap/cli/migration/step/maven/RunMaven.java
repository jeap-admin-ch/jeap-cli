package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import ch.admin.bit.jeap.cli.process.SystemProcessExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes Maven with the specified command line arguments.
 * <p>
 * This step runs Maven (mvn command) in the specified working directory with the provided arguments.
 * The Maven output is displayed to the user in real-time. If Maven exits with a non-zero status code,
 * an exception is thrown.
 * </p>
 */
public class RunMaven implements Step {

    private final Path workingDirectory;
    private final List<String> mavenArgs;
    private final ProcessExecutor processExecutor;

    /**
     * Creates a new RunMaven step with a custom process executor.
     *
     * @param workingDirectory the directory where Maven should be executed (typically the project root)
     * @param mavenArgs        the Maven command line arguments (e.g., ["clean", "install"])
     * @param processExecutor  the process executor to use for running Maven
     */
    public RunMaven(Path workingDirectory, List<String> mavenArgs, ProcessExecutor processExecutor) {
        this.workingDirectory = workingDirectory;
        this.mavenArgs = List.copyOf(mavenArgs);
        this.processExecutor = processExecutor;
    }

    /**
     * Creates a new RunMaven step.
     *
     * @param workingDirectory the directory where Maven should be executed (typically the project root)
     * @param mavenArgs        the Maven command line arguments (e.g., ["clean", "install"])
     */
    public RunMaven(Path workingDirectory, List<String> mavenArgs) {
        this(workingDirectory, mavenArgs, new SystemProcessExecutor());
    }

    /**
     * Creates a new RunMaven step with a single Maven goal.
     *
     * @param workingDirectory the directory where Maven should be executed (typically the project root)
     * @param mavenArgs        the Maven command line arguments (varargs)
     */
    public RunMaven(Path workingDirectory, String... mavenArgs) {
        this(workingDirectory, List.of(mavenArgs), new SystemProcessExecutor());
    }

    @Override
    public void execute() throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("mvn");
        command.addAll(mavenArgs);

        int exitCode = processExecutor.execute(command, workingDirectory);

        if (exitCode != 0) {
            throw new IOException("Maven command failed with exit code " + exitCode +
                    ": mvn " + String.join(" ", mavenArgs));
        }
    }

    @Override
    public String name() {
        return "Run Maven: " + String.join(" ", mavenArgs);
    }
}
