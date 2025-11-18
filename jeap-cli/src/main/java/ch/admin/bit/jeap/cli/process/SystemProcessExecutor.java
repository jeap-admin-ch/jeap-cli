package ch.admin.bit.jeap.cli.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Default implementation of {@link ProcessExecutor} that executes commands using the system's ProcessBuilder.
 * <p>
 * This implementation inherits I/O from the parent process, allowing command output to be displayed
 * to the user in real-time.
 * </p>
 */
public class SystemProcessExecutor implements ProcessExecutor {

    @Override
    public int execute(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .inheritIO(); // Redirect output to the console

        Process process = processBuilder.start();
        return process.waitFor();
    }
}
