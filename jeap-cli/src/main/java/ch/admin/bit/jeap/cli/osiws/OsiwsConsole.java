package ch.admin.bit.jeap.cli.osiws;

import org.springframework.stereotype.Component;

import java.io.PrintStream;

/**
 * Diagnostics go to stderr, so that stdout carries only the job id or the report and can be piped.
 */
@Component
class OsiwsConsole {

    private final PrintStream stderr;

    OsiwsConsole() {
        this(System.err);
    }

    OsiwsConsole(PrintStream stderr) {
        this.stderr = stderr;
    }

    void info(String message) {
        stderr.println(message);
    }

    void error(String message) {
        stderr.println("Error: " + message);
    }
}
