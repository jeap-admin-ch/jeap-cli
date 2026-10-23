package ch.admin.bit.jeap.cli.process;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link ProcessExecutor} that executes commands using the system's ProcessBuilder.
 * <p>
 * This implementation inherits I/O from the parent process, allowing command output to be displayed
 * to the user in real-time.
 * </p>
 */
@Slf4j
@Component
public class SystemProcessExecutor implements ProcessExecutor {
    private static final String CERT_REPO_TOKEN = "CERTIFICATES_REPO_GIT_TOKEN";

    @Override
    public int execute(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        return executeAndCapture(command, workingDirectory).exitCode();
    }

    @Override
    public ProcessExecutionResult executeAndCapture(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = createProcessBuilder(command, workingDirectory);
        ensureCertificateRepoToken(processBuilder.environment(), workingDirectory);
        return runProcess(processBuilder);
    }

    private ProcessBuilder createProcessBuilder(List<String> command, Path workingDirectory) {
        return new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
    }

    private ProcessExecutionResult runProcess(ProcessBuilder processBuilder) throws IOException, InterruptedException {
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

    private void ensureCertificateRepoToken(Map<String, String> environment, Path workingDirectory) {
        if (hasText(environment.get(CERT_REPO_TOKEN))) {
            return;
        }
        String resolvedToken = resolveTokenFromEnvironment(environment);
        if (!hasText(resolvedToken)) {
            resolvedToken = resolveTokenViaGhCli(workingDirectory, environment);
        }
        if (hasText(resolvedToken)) {
            environment.put(CERT_REPO_TOKEN, resolvedToken);
        } else {
            logWarn("Could not resolve CERTIFICATES_REPO_GIT_TOKEN from env or gh auth token.");
        }
    }

    private String resolveTokenFromEnvironment(Map<String, String> environment) {
        if (hasText(environment.get("GH_TOKEN"))) {
            return environment.get("GH_TOKEN");
        }
        if (hasText(environment.get("GITHUB_TOKEN"))) {
            return environment.get("GITHUB_TOKEN");
        }
        return null;
    }

    private String resolveTokenViaGhCli(Path workingDirectory, Map<String, String> parentEnvironment) {
        try {
            ProcessBuilder ghProcessBuilder = new ProcessBuilder("gh", "auth", "token")
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true);
            ghProcessBuilder.environment().putAll(parentEnvironment);

            Process ghProcess = ghProcessBuilder.start();
            String outputLine;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ghProcess.getInputStream(), StandardCharsets.UTF_8))) {
                outputLine = reader.readLine();
            }
            int exitCode = ghProcess.waitFor();
            if (exitCode == 0 && hasText(outputLine)) {
                return outputLine.trim();
            }
            logWarn("gh auth token failed (exitCode=%d)".formatted(exitCode));
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logWarn("gh auth token failed: %s".formatted(ignored.getClass().getSimpleName()));
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void logWarn(String message) {
        log.warn(message);
    }
}
