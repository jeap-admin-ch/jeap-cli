package ch.admin.bit.jeap.cli.osiws;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OsiwsExitStatusExceptionMapperTest {

    @Test
    void printsActionableErrorToStderrAndReturnsNonZeroStatus() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        OsiwsExitStatusExceptionMapper mapper = new OsiwsExitStatusExceptionMapper(
                new OsiwsConsole(new PrintStream(stderr, true, StandardCharsets.UTF_8)));

        var status = mapper.apply(new InvocationTargetException(new OsiwsException("Duplicate id '12345'.")));

        assertThat(status.code()).isNotZero();
        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .isEqualTo("Error: Duplicate id '12345'." + System.lineSeparator());
    }
}
