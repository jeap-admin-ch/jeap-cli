package ch.admin.bit.jeap.cli;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JeapCLITests {

	@Autowired
	private SimpleClientHttpRequestFactory requestFactory;

	@Test
	void contextLoads() {
        // Test to ensure the Spring application context loads successfully
        // No implementation needed; if the context fails to load, this test will fail
	}

	@Test
	void configuresFiniteHttpTimeouts() {
		assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout"))
				.isEqualTo(Math.toIntExact(JeapCLI.HTTP_CONNECT_TIMEOUT.toMillis()));
		assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout"))
				.isEqualTo(Math.toIntExact(JeapCLI.HTTP_READ_TIMEOUT.toMillis()));
	}

}
