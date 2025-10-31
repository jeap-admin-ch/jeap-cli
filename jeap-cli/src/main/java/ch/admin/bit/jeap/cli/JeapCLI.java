package ch.admin.bit.jeap.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JeapCLI {

	public static void main(String[] args) {
        SpringApplication.run(JeapCLI.class, args.length == 0 ? new String[]{"help"} : args);
	}
}
