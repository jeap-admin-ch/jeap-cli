package ch.admin.bit.jeap.cli.process;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Default implementation of {@link ProcessExecutor} that executes commands using the system's ProcessBuilder.
 * <p>
 * This implementation inherits I/O from the parent process, allowing command output to be displayed
 * to the user in real-time.
 * </p>
 */
@Component
public class SystemProcessExecutor implements ProcessExecutor {

    @Override
    public int execute(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        return executeAndCapture(command, workingDirectory).exitCode();
    }

    @Override
    public ProcessExecutionResult executeAndCapture(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);

        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                output.append(line).append(System.lineSeparator());
            }
        }

        return new ProcessExecutionResult(process.waitFor(), output.toString());
    }
}
