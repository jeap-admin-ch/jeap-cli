package ch.admin.bit.jeap.cli.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Fake implementation of {@link ProcessExecutor} for testing purposes.
 * <p>
 * This implementation allows tests to control the exit code and capture executed commands
 * without actually executing external processes.
 * </p>
 */
public class FakeProcessExecutor implements ProcessExecutor {

    private final List<ExecutedCommand> executedCommands = new ArrayList<>();
    private BiFunction<List<String>, Path, Integer> exitCodeProvider;
    private BiFunction<List<String>, Path, String> outputProvider;

    /**
     * Creates a FakeProcessExecutor that always returns exit code 0 (success).
     */
    public FakeProcessExecutor() {
        this((cmd, dir) -> 0, (cmd, dir) -> "");
    }

    /**
     * Creates a FakeProcessExecutor with a fixed exit code.
     *
     * @param exitCode the exit code to return for all executions
     */
    public FakeProcessExecutor(int exitCode) {
        this((cmd, dir) -> exitCode, (cmd, dir) -> "");
    }

    /**
     * Creates a FakeProcessExecutor with a fixed exit code and fixed output.
     */
    public FakeProcessExecutor(int exitCode, String output) {
        this((cmd, dir) -> exitCode, (cmd, dir) -> output);
    }

    /**
     * Creates a FakeProcessExecutor with a custom exit code provider.
     *
     * @param exitCodeProvider function that takes command and working directory and returns an exit code
     */
    public FakeProcessExecutor(BiFunction<List<String>, Path, Integer> exitCodeProvider) {
        this(exitCodeProvider, (cmd, dir) -> "");
    }

    public FakeProcessExecutor(BiFunction<List<String>, Path, Integer> exitCodeProvider,
                               BiFunction<List<String>, Path, String> outputProvider) {
        this.exitCodeProvider = exitCodeProvider;
        this.outputProvider = outputProvider;
    }

    @Override
    public int execute(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        return run(command, workingDirectory).exitCode();
    }

    @Override
    public ProcessExecutionResult executeAndCapture(List<String> command, Path workingDirectory)
            throws IOException, InterruptedException {
        return run(command, workingDirectory);
    }

    private ProcessExecutionResult run(List<String> command, Path workingDirectory) {
        ExecutedCommand executedCommand = new ExecutedCommand(List.copyOf(command), workingDirectory);
        executedCommands.add(executedCommand);
        return new ProcessExecutionResult(exitCodeProvider.apply(command, workingDirectory),
                outputProvider.apply(command, workingDirectory));
    }

    /**
     * Returns all commands that were executed.
     *
     * @return list of executed commands
     */
    public List<ExecutedCommand> getExecutedCommands() {
        return List.copyOf(executedCommands);
    }

    /**
     * Returns the last executed command, or null if no commands were executed.
     *
     * @return the last executed command
     */
    public ExecutedCommand getLastExecutedCommand() {
        return executedCommands.isEmpty() ? null : executedCommands.get(executedCommands.size() - 1);
    }

    /**
     * Clears the history of executed commands.
     */
    public void clearHistory() {
        executedCommands.clear();
    }

    /**
     * Returns the number of commands that were executed.
     *
     * @return the number of executed commands
     */
    public int getExecutionCount() {
        return executedCommands.size();
    }

    /**
     * Record of a command execution.
     */
    public record ExecutedCommand(List<String> command, Path workingDirectory) {
    }
}
