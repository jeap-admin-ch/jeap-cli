package ch.admin.bit.jeap.cli.pcs;

import org.springframework.stereotype.Component;

import java.io.PrintStream;

@Component
class PcsConsole {

    private final PrintStream stderr;

    PcsConsole() {
        this(System.err);
    }

    PcsConsole(PrintStream stderr) {
        this.stderr = stderr;
    }

    void warn(String message) {
        stderr.println("Warning: " + message);
    }

    void error(String message) {
        stderr.println("Error: " + message);
    }
}
