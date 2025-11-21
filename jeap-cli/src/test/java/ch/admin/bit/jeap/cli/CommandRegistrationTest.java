package ch.admin.bit.jeap.cli;

import ch.admin.bit.jeap.cli.migration.process.Java25Migration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.shell.test.ShellAssertions;
import org.springframework.shell.test.ShellTestClient;
import org.springframework.shell.test.ShellTestClient.NonInteractiveShellSession;
import org.springframework.shell.test.autoconfigure.ShellTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@ShellTest
@Import(JeapCLI.class)
class CommandRegistrationTest {

    @Autowired
    ShellTestClient client;

    @MockitoBean
    Java25Migration java25Migration;

    @Test
    void test() {
        NonInteractiveShellSession session = client
                .nonInterative("help")
                .run();

        await().atMost(15, SECONDS).untilAsserted(() ->
                ShellAssertions.assertThat(session.screen())
                        .containsText("migrate java-25")
                        .containsText("AVAILABLE COMMANDS"));
    }
}
