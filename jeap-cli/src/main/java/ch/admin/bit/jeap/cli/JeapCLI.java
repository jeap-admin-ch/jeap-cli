package ch.admin.bit.jeap.cli;

import ch.admin.bit.jeap.cli.commands.MigrationCommands;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.command.annotation.EnableCommand;

@SpringBootApplication
@EnableCommand(MigrationCommands.class)
public class JeapCLI {

	public static void main(String[] args) {
        SpringApplication.run(JeapCLI.class, args.length == 0 ? new String[]{"help"} : args);
	}
}
