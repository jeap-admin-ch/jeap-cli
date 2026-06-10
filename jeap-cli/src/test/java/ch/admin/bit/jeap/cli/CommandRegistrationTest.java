package ch.admin.bit.jeap.cli;

import ch.admin.bit.jeap.cli.migration.process.Java25Migration;
import ch.admin.bit.jeap.cli.migration.process.SpringBoot4Migration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.shell.test.ShellAssertions;
import org.springframework.shell.test.ShellScreen;
import org.springframework.shell.test.ShellTestClient;
import org.springframework.shell.test.autoconfigure.ShellTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ShellTest
@Import(JeapCLI.class)
class CommandRegistrationTest {

    @Autowired
    ShellTestClient client;

    @MockitoBean
    Java25Migration java25Migration;

    @MockitoBean
    SpringBoot4Migration springBoot4Migration;

    @Test
    void test() throws Exception {
        ShellScreen screen = client.sendCommand("help");

        ShellAssertions.assertThat(screen)
                .containsText("migrate java-25")
                .containsText("migrate spring-boot-4")
                .containsText("AVAILABLE COMMANDS");
    }
}
