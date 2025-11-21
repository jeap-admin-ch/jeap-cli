package ch.admin.bit.jeap.cli.commands;

import ch.admin.bit.jeap.cli.migration.process.Java25Migration;
import org.springframework.shell.command.annotation.Command;

import java.nio.file.Path;
import java.nio.file.Paths;

@Command(group = "Migrations")
public class MigrationCommands {

    private final Java25Migration java25Migration;

    public MigrationCommands(Java25Migration java25Migration) {
        this.java25Migration = java25Migration;
    }

    @Command(description = "Migrate jEAP application to Java 25", command = "migrate java-25")
    public void migrateToJava25() throws Exception {
        Path cwd = Paths.get(".");
        java25Migration.migrate(cwd);
    }
}
