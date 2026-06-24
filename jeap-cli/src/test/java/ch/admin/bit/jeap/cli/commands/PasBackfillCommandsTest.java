package ch.admin.bit.jeap.cli.commands;

import ch.admin.bit.jeap.cli.backfill.PasBackfillException;
import ch.admin.bit.jeap.cli.backfill.PasBackfillService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasBackfillCommandsTest {

    private static final String JOB_ID = "88dbb65f-9634-4685-bc86-17b72d715d3e";
    private static final String BASE_URL = "https://pas.example.com";

    @Test
    void sendReadsAccessTokenFromStdin() {
        PasBackfillService service = mock(PasBackfillService.class);
        when(service.send(eq(Path.of("backfill-job.yaml")), eq(null), eq(JOB_ID), eq(BASE_URL), eq("stdin-token")))
                .thenReturn("ok");
        PasBackfillCommands commands = commands(service, "stdin-token\n");

        commands.send("backfill-job.yaml", null, JOB_ID, BASE_URL, null);

        verify(service).send(Path.of("backfill-job.yaml"), null, JOB_ID, BASE_URL, "stdin-token");
    }

    @Test
    void sendForwardsReferencesCsv() {
        PasBackfillService service = mock(PasBackfillService.class);
        when(service.send(eq(Path.of("backfill-job.yaml")), eq(Path.of("references.csv")), eq(JOB_ID), eq(BASE_URL), eq("stdin-token")))
                .thenReturn("ok");
        PasBackfillCommands commands = commands(service, "stdin-token\n");

        commands.send("backfill-job.yaml", "references.csv", JOB_ID, BASE_URL, null);

        verify(service).send(Path.of("backfill-job.yaml"), Path.of("references.csv"), JOB_ID, BASE_URL, "stdin-token");
    }

    @Test
    void reportReadsAccessTokenFromStdin() {
        PasBackfillService service = mock(PasBackfillService.class);
        when(service.report(eq(JOB_ID), eq(BASE_URL), eq(null), eq("stdin-token")))
                .thenReturn("report");
        PasBackfillCommands commands = commands(service, "stdin-token\n");

        commands.report(JOB_ID, BASE_URL, null, null);

        verify(service).report(JOB_ID, BASE_URL, null, "stdin-token");
    }

    @Test
    void accessTokenOptionTakesPrecedenceOverStdin() {
        PasBackfillService service = mock(PasBackfillService.class);
        when(service.report(eq(JOB_ID), eq(BASE_URL), eq(null), eq("option-token")))
                .thenReturn("report");
        PasBackfillCommands commands = commands(service, "stdin-token\n");

        commands.report(JOB_ID, BASE_URL, null, "option-token");

        verify(service).report(JOB_ID, BASE_URL, null, "option-token");
    }

    @Test
    void missingAccessTokenFails() {
        PasBackfillCommands commands = commands(mock(PasBackfillService.class), "\n");

        assertThatThrownBy(() -> commands.report(JOB_ID, BASE_URL, null, null))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("Missing access token. Provide --access-token or pipe the token to stdin.");
    }

    private PasBackfillCommands commands(PasBackfillService service, String stdin) {
        return new PasBackfillCommands(service, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
    }
}
