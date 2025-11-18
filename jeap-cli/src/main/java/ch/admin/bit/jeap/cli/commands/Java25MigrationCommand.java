package ch.admin.bit.jeap.cli.commands;

import ch.admin.bit.jeap.cli.migration.process.Java25Migration;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.nio.file.Path;
import java.nio.file.Paths;

@ShellComponent
public class Java25MigrationCommand {

    private final Java25Migration java25Migration;

    public Java25MigrationCommand(Java25Migration java25Migration) {
        this.java25Migration = java25Migration;
    }

    @ShellMethod(
            value = "Migrate jEAP application to Java 25",
            key = {"migrate java-25", "java-25"},
            group = "migrate"
    )
    public void migrateToJava25() throws Exception {
        Path cwd = Paths.get(".");
        java25Migration.migrate(cwd);
    }
}
