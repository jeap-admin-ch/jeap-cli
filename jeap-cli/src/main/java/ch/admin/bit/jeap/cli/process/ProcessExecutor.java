package ch.admin.bit.jeap.cli.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for executing external processes.
 * <p>
 * This abstraction allows for easier testing by enabling injection of fake or mock implementations.
 * </p>
 */
public interface ProcessExecutor {

    /**
     * Executes a command in the specified working directory.
     *
     * @param command          the command and its arguments to execute (e.g., ["mvn", "clean", "install"])
     * @param workingDirectory the directory where the command should be executed
     * @return the exit code of the process (0 typically indicates success)
     * @throws IOException          if an I/O error occurs during process execution
     * @throws InterruptedException if the current thread is interrupted while waiting for the process
     */
    int execute(List<String> command, Path workingDirectory) throws IOException, InterruptedException;
}
