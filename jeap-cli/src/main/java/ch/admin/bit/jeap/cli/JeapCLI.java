package ch.admin.bit.jeap.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@SpringBootApplication
public class JeapCLI {

	static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);
	static final Duration HTTP_READ_TIMEOUT = Duration.ofSeconds(60);
	private static final String JLINE_DUMB_TERMINAL = "org.jline.terminal.dumb";

	static void main(String[] args) {
		// Suppress the jline warning when no terminal is attached, e.g. when the output is piped (https://github.com/jline/jline3/issues/291)
		if (System.getProperty(JLINE_DUMB_TERMINAL) == null) {
			System.setProperty(JLINE_DUMB_TERMINAL, "true");
		}
		ConfigurableApplicationContext context = SpringApplication.run(
				JeapCLI.class, args.length == 0 ? new String[]{"help"} : args);
		int exitCode = context.getBean(SanitizedShellApplicationRunner.class).exitCode();
		context.close();
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

    @Bean
    SimpleClientHttpRequestFactory clientHttpRequestFactory() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(HTTP_CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(HTTP_READ_TIMEOUT);
		return requestFactory;
	}

    @Bean
    RestClient.Builder restClientBuilder(SimpleClientHttpRequestFactory requestFactory) {
		return RestClient.builder().requestFactory(requestFactory);
    }
}
