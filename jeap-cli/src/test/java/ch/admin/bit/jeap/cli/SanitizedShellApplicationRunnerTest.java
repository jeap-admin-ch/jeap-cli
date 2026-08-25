package ch.admin.bit.jeap.cli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandExecutionException;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.ParsedInput;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SanitizedShellApplicationRunnerTest {

    @Test
    void removesSeparatedAccessTokenFromFailure() {
        assertTokenIsRedacted("--access-token secret-value");
    }

    @Test
    void removesEqualsAccessTokenFromFailure() {
        assertTokenIsRedacted("--access-token=secret-value");
    }

    @Test
    void rejectsUnknownPcsCommandWithoutCallingShell() throws Exception {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ShellRunner shellRunner = args -> {
            throw new AssertionError("Shell must not be called");
        };
        SanitizedShellApplicationRunner runner = new SanitizedShellApplicationRunner(
                shellRunner, successfulParser(), new PrintStream(stderr, true, StandardCharsets.UTF_8));

        runner.run(new DefaultApplicationArguments("pcs", "typo"));

        assertThat(runner.exitCode()).isEqualTo(2);
        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("Unknown PCS command 'typo'");
    }

    @Test
    void handlesUnexpectedShellFailureWithoutPropagatingIt() throws Exception {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ShellRunner shellRunner = args -> {
            throw new IllegalArgumentException("Unknown command 'typo'");
        };
        SanitizedShellApplicationRunner runner = new SanitizedShellApplicationRunner(
                shellRunner, successfulParser(), new PrintStream(stderr, true, StandardCharsets.UTF_8));

        runner.run(new DefaultApplicationArguments("typo"));

        assertThat(runner.exitCode()).isEqualTo(1);
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Unknown command 'typo'");
    }

    @Test
    void reportsCommandParsingFailure() throws Exception {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ShellRunner shellRunner = args -> {
            throw new AssertionError("Shell must not be called");
        };
        CommandParser commandParser = command -> {
            throw new IllegalArgumentException("Missing value for option '--job-id'");
        };
        SanitizedShellApplicationRunner runner = new SanitizedShellApplicationRunner(
                shellRunner, commandParser, new PrintStream(stderr, true, StandardCharsets.UTF_8));

        runner.run(new DefaultApplicationArguments("pcs", "report", "--job-id"));

        assertThat(runner.exitCode()).isEqualTo(1);
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Missing value for option '--job-id'");
    }

    @Test
    void preservesArgumentsContainingSpaces() throws Exception {
        SanitizedShellApplicationRunner runner = new SanitizedShellApplicationRunner(
                args -> assertThat(args).containsExactly("pcs", "report", "--output=\"/tmp/job report.yaml\""),
                successfulParser(), System.err);

        runner.run(new DefaultApplicationArguments("pcs", "report", "--output=/tmp/job report.yaml"));

        assertThat(runner.exitCode()).isZero();
    }

    private void assertTokenIsRedacted(String tokenArgument) {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ShellRunner shellRunner = args -> {
            throw new CommandExecutionException("Unable to execute pcs report",
                    new IllegalStateException("Request failed with " + tokenArgument));
        };
        SanitizedShellApplicationRunner runner = new SanitizedShellApplicationRunner(
                shellRunner, successfulParser(), new PrintStream(stderr, true, StandardCharsets.UTF_8));

        try {
            runner.run(new DefaultApplicationArguments("migrate", "test"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        assertThat(runner.exitCode()).isEqualTo(1);
        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("[REDACTED]")
                .doesNotContain("secret-value");
    }

    private CommandParser successfulParser() {
        return command -> mock(ParsedInput.class);
    }
}
