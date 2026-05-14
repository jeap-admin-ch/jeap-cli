package ch.admin.bit.jeap.cli.migration.step.maven;

import java.io.IOException;

/**
 * Thrown when a Maven command exits with a non-zero code.
 */
public class MavenCommandException extends IOException {

    private final int exitCode;
    private final String command;
    private final String output;

    public MavenCommandException(int exitCode, String command, String output) {
        super("Maven command failed with exit code " + exitCode + ": " + command);
        this.exitCode = exitCode;
        this.command = command;
        this.output = output;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getCommand() {
        return command;
    }

    public String getOutput() {
        return output;
    }
}
