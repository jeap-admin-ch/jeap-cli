package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.step.maven.MavenCommandException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Uses Copilot CLI to attempt fixes for Maven migration failures.
 */
@Slf4j
public class CopilotCliMavenFailureAutoFixer implements MavenFailureAutoFixer {

    private static final int MAX_FIX_SUMMARY_LINES = 40;
    private static final List<String> AUTH_STATUS_COMMAND = List.of("gh", "auth", "status");
    private static final String COPILOT_HEALTH_PROMPT = "Please answer exactly with this single word: test";
    private static final List<String> GH_AUTH_LOGIN_WEB_COMMAND = List.of("gh", "auth", "login", "--web");
    private static final List<String> COPILOT_LOGIN_COMMAND = List.of("copilot", "login");
    private static final List<String> COPILOT_INSTALL_COMMAND =
            List.of("bash", "-lc", "curl -fsSL https://gh.io/copilot-install | bash");

    private final CliCommandRunner commandRunner;

    public CopilotCliMavenFailureAutoFixer() {
        this(new SystemCliCommandRunner());
    }

    CopilotCliMavenFailureAutoFixer(CliCommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    @Override
    public boolean prepare(Path root) throws Exception {
        if (isCopilotCliReady(root)) {
            return true;
        }

        log.warn("Copilot CLI is not ready in the container. Installing GitHub Copilot CLI now.");
        if (!runNonInteractive(root, COPILOT_INSTALL_COMMAND, "Copilot CLI installation")) {
            return false;
        }

        log.warn("Please complete authentication in the container now.");
        if (!runInteractive(root, GH_AUTH_LOGIN_WEB_COMMAND, "gh auth login --web")) {
            return false;
        }
        if (!runInteractive(root, COPILOT_LOGIN_COMMAND, "copilot login")) {
            return false;
        }

        if (!isCopilotCliReady(root)) {
            log.error("Copilot CLI setup completed but validation still fails. Aborting.");
            return false;
        }
        return true;
    }

    @Override
    public boolean tryFix(Path root, MavenCommandException failure, int attempt, int maxRetries) throws Exception {
        String stackTrace = extractStackTrace(failure.getOutput());

        log.warn("Auto-fix iteration {}/{} after Maven failure.", attempt, maxRetries);
        log.warn("Failed command: {}", failure.getCommand());
        log.warn("Extracted stacktrace:\n{}", stackTrace);

        String fixPrompt = buildFixPrompt(stackTrace);
        CommandResult fixResult = commandRunner.runCopilotPrompt(root, fixPrompt);
        if (fixResult.exitCode() != 0) {
            log.error("copilot fix prompt failed with exit code {}.", fixResult.exitCode());
            if (!fixResult.output().isBlank()) {
                log.error("Copilot output:\n{}", summarizeFixOutput(fixResult.output()));
            }
            return false;
        }

        String fixSummary = summarizeFixOutput(fixResult.output());
        if (fixSummary.isBlank()) {
            log.info("Copilot fix command succeeded.");
        } else {
            log.info("Applied fix:\n{}", fixSummary);
        }
        return true;
    }

    private boolean isCopilotCliReady(Path root) throws Exception {
        CommandResult authStatus = commandRunner.run(root, AUTH_STATUS_COMMAND, false);
        if (authStatus.exitCode() != 0) {
            log.warn("Copilot validation failed: gh auth status exited with {}.", authStatus.exitCode());
            return false;
        }
        CommandResult copilotHealthcheck = commandRunner.runCopilotPrompt(root, COPILOT_HEALTH_PROMPT);
        if (copilotHealthcheck.exitCode() != 0) {
            log.warn("Copilot validation failed: copilot prompt execution exited with {}.", copilotHealthcheck.exitCode());
            return false;
        }
        return true;
    }

    private boolean runNonInteractive(Path root, List<String> command, String printableCommand) throws Exception {
        CommandResult result = commandRunner.run(root, command, false);
        if (result.exitCode() != 0) {
            log.error("{} failed with exit code {}.", printableCommand, result.exitCode());
            if (!result.output().isBlank()) {
                log.error("Command output:\n{}", summarizeFixOutput(result.output()));
            }
            return false;
        }
        return true;
    }

    private boolean runInteractive(Path root, List<String> command, String printableCommand) throws Exception {
        CommandResult result = commandRunner.run(root, command, true);
        if (result.exitCode() != 0) {
            log.error("{} failed with exit code {}.", printableCommand, result.exitCode());
            return false;
        }
        return true;
    }

    private String extractStackTrace(String output) {
        if (output == null || output.isBlank()) {
            return "(no Maven output available)";
        }
        String[] lines = output.split("\\R");
        OptionalInt firstErrorLine = IntStream.range(0, lines.length)
                .filter(i -> lines[i].contains("[ERROR]"))
                .findFirst();
        OptionalInt firstStackTraceLine = IntStream.range(0, lines.length)
                .filter(i -> lines[i].contains("Exception")
                        || lines[i].contains("Caused by:")
                        || lines[i].trim().startsWith("at "))
                .findFirst();

        int from = IntStream.concat(firstErrorLine.stream(), firstStackTraceLine.stream())
                .min()
                .orElse(0);
        return String.join(System.lineSeparator(), List.of(lines).subList(from, lines.length));
    }

    private String buildFixPrompt(String stackTrace) {
        return "You are fixing a Spring Boot 4 migration failure in this Maven project. " +
                "Apply concrete file changes directly in the working tree and then exit." + System.lineSeparator() +
                System.lineSeparator() +
                "Maven stacktrace/error output:" + System.lineSeparator() +
                stackTrace + System.lineSeparator() +
                System.lineSeparator() +
                "Focus only on changes required to make the migration pass; avoid unrelated refactors.";
    }

    private String summarizeFixOutput(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        String[] lines = output.split("\\R");
        int from = Math.max(0, lines.length - MAX_FIX_SUMMARY_LINES);
        return IntStream.range(from, lines.length)
                .mapToObj(i -> lines[i])
                .collect(Collectors.joining(System.lineSeparator()));
    }

    interface CliCommandRunner {
        CommandResult run(Path root, List<String> command, boolean interactive) throws Exception;

        CommandResult runCopilotPrompt(Path root, String prompt) throws Exception;
    }

    record CommandResult(int exitCode, String output) {
    }

    static class SystemCliCommandRunner implements CliCommandRunner {
        @Override
        public CommandResult run(Path root, List<String> command, boolean interactive) throws Exception {
            ProcessBuilder processBuilder = new ProcessBuilder(command).directory(root.toFile());
            if (interactive) {
                processBuilder.inheritIO();
                Process process = processBuilder.start();
                return new CommandResult(process.waitFor(), "");
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    output.append(line).append(System.lineSeparator());
                }
            }

            return new CommandResult(process.waitFor(), output.toString());
        }

        @Override
        public CommandResult runCopilotPrompt(Path root, String prompt) throws Exception {
            // Run in non-interactive mode with full permissions so auto-fix can edit files
            // without waiting for permission prompts.
            return run(root, List.of("copilot", "-p", prompt, "--allow-all", "--no-ask-user"), false);
        }
    }
}
