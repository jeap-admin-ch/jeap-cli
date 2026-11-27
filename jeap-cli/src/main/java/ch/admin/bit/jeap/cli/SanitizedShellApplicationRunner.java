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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component("springShellApplicationRunner")
class SanitizedShellApplicationRunner implements ApplicationRunner {

    private static final Pattern TOKEN_WITH_EQUALS = Pattern.compile("(--access-token=)[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_WITH_SPACE = Pattern.compile("(--access-token\\s+)[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> HELP_OPTIONS = Set.of("--help", "-h");
    // Command groups whose exit status exception mapper prints the error itself
    private static final Map<String, CommandGroup> COMMAND_GROUPS = Map.of(
            "pcs", new CommandGroup("PCS", List.of("reevaluate-relations", "backfill", "notify-relations", "report")),
            "osiws", new CommandGroup("osiws", List.of("upsert", "delete", "report")));

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
        CommandGroup group = sourceArgs.length > 0 ? COMMAND_GROUPS.get(sourceArgs[0]) : null;
        if (group != null && sourceArgs.length > 1 && !group.commands().contains(sourceArgs[1])
                && !HELP_OPTIONS.contains(sourceArgs[1])) {
            stderr.println("Error: Unknown " + group.label() + " command '" + sourceArgs[1] + "'. Expected "
                    + group.expectedCommands() + ".");
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
            if (group == null) {
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

    private record CommandGroup(String label, List<String> commands) {

        String expectedCommands() {
            return String.join(", ", commands.subList(0, commands.size() - 1)) + " or " + commands.getLast();
        }
    }
}
