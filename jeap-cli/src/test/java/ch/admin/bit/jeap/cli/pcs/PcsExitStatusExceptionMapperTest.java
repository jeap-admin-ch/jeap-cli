package ch.admin.bit.jeap.cli.pcs;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PcsExitStatusExceptionMapperTest {

    @Test
    void printsActionableErrorToStderrAndReturnsNonZeroStatus() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PcsExitStatusExceptionMapper mapper = new PcsExitStatusExceptionMapper(
                new PcsConsole(new PrintStream(stderr, true, StandardCharsets.UTF_8)));

        var status = mapper.apply(new InvocationTargetException(new PcsException("Invalid relationId.")));

        assertThat(status.code()).isNotZero();
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEqualTo("Error: Invalid relationId.\n");
    }
}
