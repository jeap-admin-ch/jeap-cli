package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.step.maven.MavenCommandException;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Uses Copilot CLI to attempt fixes for Maven migration failures.
 */
@Slf4j
public class CopilotCliMavenFailureAutoFixer implements MavenFailureAutoFixer {

    static final String COPILOT_COMMAND_ENV = "JEAP_COPILOT_CLI_COMMAND";
    static final String DEFAULT_COPILOT_COMMAND = "copilot";

    @Override
    public boolean tryFix(Path root, MavenCommandException failure, int attempt, int maxRetries) throws Exception {
        String copilotCommand = System.getenv().getOrDefault(COPILOT_COMMAND_ENV, DEFAULT_COPILOT_COMMAND);
        String prompt = buildPrompt(root, failure, attempt, maxRetries);

        log.warn("Auto-fix attempt {}/{} after Maven failure. Running command: {}", attempt, maxRetries, copilotCommand);

        Process process = new ProcessBuilder("bash", "-lc", copilotCommand)
                .directory(root.toFile())
                .inheritIO()
                .start();

        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(prompt);
            writer.flush();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("Copilot CLI command failed with exit code {}. Stopping migration retry loop.", exitCode);
            return false;
        }
        return true;
    }

    private String buildPrompt(Path root, MavenCommandException failure, int attempt, int maxRetries) {
        return "You are fixing a Spring Boot 4 migration failure in this Maven project. " +
                "Apply concrete file changes directly in the working tree and then exit.\n\n" +
                "Project root: " + root.toAbsolutePath() + "\n" +
                "Retry attempt: " + attempt + " of " + maxRetries + "\n" +
                "Failed command: " + failure.getCommand() + "\n\n" +
                "Maven output tail:\n" +
                failure.getOutput() + "\n\n" +
                "Focus only on fixes required to continue migration; avoid unrelated refactors.";
    }
}
