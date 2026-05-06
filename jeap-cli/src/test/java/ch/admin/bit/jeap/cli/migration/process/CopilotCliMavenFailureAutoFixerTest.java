package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.step.maven.MavenCommandException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotCliMavenFailureAutoFixerTest {

    @TempDir
    Path tempDir;

    @Test
    void testPrepareDoesNotLoginWhenCopilotCliAlreadyWorks() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        runner.addCommandResult("gh auth status", 0, "ok");
        runner.addCopilotPromptResult("Please answer exactly with this single word: test", 0, "test");

        CopilotCliMavenFailureAutoFixer fixer = new CopilotCliMavenFailureAutoFixer(runner);

        assertTrue(fixer.prepare(tempDir));
        assertEquals(List.of("gh auth status", "copilot::<PROMPT>"), runner.executedCommands());
        assertEquals(List.of(false), runner.interactiveFlags());
    }

    @Test
    void testPrepareRunsLoginAndSetupWhenValidationFailsInitially() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        runner.addCommandResult("gh auth status", 1, "not logged in");
        runner.addCommandResult("bash -lc curl -fsSL https://gh.io/copilot-install | bash", 0, "");
        runner.addCommandResult("gh auth login --web", 0, "");
        runner.addCommandResult("copilot", 0, "");
        runner.addCommandResult("gh auth status", 0, "ok");
        runner.addCopilotPromptResult("Please answer exactly with this single word: test", 0, "test");

        CopilotCliMavenFailureAutoFixer fixer = new CopilotCliMavenFailureAutoFixer(runner);

        assertTrue(fixer.prepare(tempDir));
        assertEquals(List.of(
                        "gh auth status",
                        "bash -lc curl -fsSL https://gh.io/copilot-install | bash",
                        "gh auth login --web",
                        "copilot",
                        "gh auth status",
                        "copilot::<PROMPT>"),
                runner.executedCommands());
        assertEquals(List.of(false, false, true, true, false), runner.interactiveFlags());
    }

    @Test
    void testPrepareFailsWhenLoginFails() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        runner.addCommandResult("gh auth status", 1, "not logged in");
        runner.addCommandResult("bash -lc curl -fsSL https://gh.io/copilot-install | bash", 0, "");
        runner.addCommandResult("gh auth login --web", 1, "login failed");

        CopilotCliMavenFailureAutoFixer fixer = new CopilotCliMavenFailureAutoFixer(runner);

        assertFalse(fixer.prepare(tempDir));
        assertEquals(List.of(
                        "gh auth status",
                        "bash -lc curl -fsSL https://gh.io/copilot-install | bash",
                        "gh auth login --web"),
                runner.executedCommands());
        assertEquals(List.of(false, false, true), runner.interactiveFlags());
    }

    @Test
    void testTryFixPassesExtractedStackTraceToCopilot() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        runner.addCopilotPromptResult("Maven stacktrace/error output:", 0, "Updated failing code");
        CopilotCliMavenFailureAutoFixer fixer = new CopilotCliMavenFailureAutoFixer(runner);

        MavenCommandException failure = new MavenCommandException(1, "mvn test", """
                [INFO] Running tests
                [ERROR] Failed to execute goal
                java.lang.IllegalStateException: Boom
                \tat com.example.Test.test(Test.java:12)
                """);

        assertTrue(fixer.tryFix(tempDir, failure, 1, 3));
        assertEquals(1, runner.prompts.size());
        String passedInput = runner.prompts.get(0);
        assertTrue(passedInput.contains("[ERROR] Failed to execute goal"));
        assertTrue(passedInput.contains("java.lang.IllegalStateException: Boom"));
        assertFalse(passedInput.contains("[INFO] Running tests"));
    }

    @Test
    void testTryFixReturnsFalseWhenCopilotFails() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        runner.addCopilotPromptResult("Maven stacktrace/error output:", 1, "no fix");
        CopilotCliMavenFailureAutoFixer fixer = new CopilotCliMavenFailureAutoFixer(runner);

        MavenCommandException failure = new MavenCommandException(1, "mvn test", "[ERROR] compile failed");

        assertFalse(fixer.tryFix(tempDir, failure, 1, 3));
    }

    private static class RecordingRunner implements CopilotCliMavenFailureAutoFixer.CliCommandRunner {

        private final List<CommandExpectation> commandExpectations = new ArrayList<>();
        private final List<CopilotPromptExpectation> promptExpectations = new ArrayList<>();
        private final List<List<String>> commands = new ArrayList<>();
        private final List<String> prompts = new ArrayList<>();
        private final List<Boolean> interactiveFlags = new ArrayList<>();

        private void addCommandResult(String command, int exitCode, String output) {
            commandExpectations.add(new CommandExpectation(command, exitCode, output));
        }

        private void addCopilotPromptResult(String promptMustContain, int exitCode, String output) {
            promptExpectations.add(new CopilotPromptExpectation(promptMustContain, exitCode, output));
        }

        @Override
        public CopilotCliMavenFailureAutoFixer.CommandResult run(Path root, List<String> command, boolean interactive) {
            commands.add(command);
            interactiveFlags.add(interactive);
            CommandExpectation expectation = commandExpectations.remove(0);

            String renderedCommand = String.join(" ", command);
            boolean matches = renderedCommand.equals(expectation.command);
            if (!matches) {
                throw new AssertionError("Unexpected command. Expected '" + expectation.command +
                        "' but got '" + renderedCommand + "'");
            }
            return new CopilotCliMavenFailureAutoFixer.CommandResult(expectation.exitCode, expectation.output);
        }

        @Override
        public CopilotCliMavenFailureAutoFixer.CommandResult runCopilotPrompt(Path root, String prompt) {
            prompts.add(prompt);
            CopilotPromptExpectation expectation = promptExpectations.remove(0);
            if (!prompt.contains(expectation.promptMustContain)) {
                throw new AssertionError("Unexpected prompt. Expected to contain '" + expectation.promptMustContain +
                        "' but got '" + prompt + "'");
            }
            return new CopilotCliMavenFailureAutoFixer.CommandResult(expectation.exitCode, expectation.output);
        }

        private List<String> executedCommands() {
            List<String> renderedCommands = commands.stream().map(c -> String.join(" ", c)).collect(Collectors.toCollection(ArrayList::new));
            for (int i = 0; i < prompts.size(); i++) {
                renderedCommands.add("copilot::<PROMPT>");
            }
            return renderedCommands;
        }

        private List<Boolean> interactiveFlags() {
            return List.copyOf(interactiveFlags);
        }

        private record CommandExpectation(String command, int exitCode, String output) {
        }

        private record CopilotPromptExpectation(String promptMustContain, int exitCode, String output) {
        }
    }
}
