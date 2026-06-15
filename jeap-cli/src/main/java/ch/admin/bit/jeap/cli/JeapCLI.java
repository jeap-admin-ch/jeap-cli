package ch.admin.bit.jeap.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class JeapCLI {

	static void main(String[] args) {
        SpringApplication.run(JeapCLI.class, args.length == 0 ? new String[]{"help"} : args);
	}

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
