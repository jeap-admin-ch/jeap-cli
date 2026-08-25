package ch.admin.bit.jeap.cli;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandExecutionException;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

@Component("springShellApplicationRunner")
class SanitizedShellApplicationRunner implements ApplicationRunner {

    private static final Pattern TOKEN_WITH_EQUALS = Pattern.compile("(--access-token=)[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_WITH_SPACE = Pattern.compile("(--access-token\\s+)[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> PCS_COMMANDS = Set.of(
            "reevaluate-relations", "backfill", "notify-relations", "report", "--help", "-h");

    private final ShellRunner shellRunner;
    private final CommandParser commandParser;
    private final PrintStream stderr;
    private int exitCode;

    @Autowired
    SanitizedShellApplicationRunner(ShellRunner shellRunner, CommandParser commandParser) {
        this(shellRunner, commandParser, System.err);
    }

    SanitizedShellApplicationRunner(ShellRunner shellRunner, CommandParser commandParser, PrintStream stderr) {
        this.shellRunner = shellRunner;
        this.commandParser = commandParser;
        this.stderr = stderr;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String[] sourceArgs = args.getSourceArgs();
        if (sourceArgs.length > 1 && "pcs".equals(sourceArgs[0]) && !PCS_COMMANDS.contains(sourceArgs[1])) {
            stderr.println("Error: Unknown PCS command '" + sourceArgs[1]
                    + "'. Expected reevaluate-relations, backfill, notify-relations or report.");
            exitCode = 2;
            return;
        }
        try {
            String[] quotedArgs = Arrays.stream(sourceArgs)
                    .map(SanitizedShellApplicationRunner::quoteArgument)
                    .toArray(String[]::new);
            commandParser.parse(String.join(" ", quotedArgs));
            shellRunner.run(quotedArgs);
        } catch (CommandExecutionException e) {
            exitCode = e.getExitCode() == 0 ? 1 : Math.abs(e.getExitCode());
            if (!"pcs".equals(sourceArgs[0])) {
                stderr.println("Error: " + redactAccessToken(rootCause(e).getMessage()));
            }
        } catch (Exception e) {
            exitCode = 1;
            stderr.println("Error: " + redactAccessToken(rootCause(e).getMessage()));
        }
    }

    int exitCode() {
        return exitCode;
    }

    static String redactAccessToken(String message) {
        if (message == null) {
            return "Command execution failed.";
        }
        String redacted = TOKEN_WITH_EQUALS.matcher(message).replaceAll("$1[REDACTED]");
        return TOKEN_WITH_SPACE.matcher(redacted).replaceAll("$1[REDACTED]");
    }

    private static String quoteArgument(String argument) {
        if (argument.chars().noneMatch(Character::isWhitespace)) {
            return argument;
        }
        int equalsIndex = argument.indexOf('=');
        if (argument.startsWith("--") && equalsIndex > 2) {
            return argument.substring(0, equalsIndex + 1) + quote(argument.substring(equalsIndex + 1));
        }
        return quote(argument);
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }
}
