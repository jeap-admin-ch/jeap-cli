package ch.admin.bit.jeap.cli.commands;

import ch.admin.bit.jeap.cli.osiws.OsiwsException;
import ch.admin.bit.jeap.cli.osiws.OsiwsJobType;
import ch.admin.bit.jeap.cli.osiws.OsiwsMaintenanceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandOption;
import org.springframework.shell.core.command.ParsedInput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OsiwsCommandsTest {

    private static final String JOB_ID = "88dbb65f-9634-4685-bc86-17b72d715d3e";
    private static final String BASE_URL = "https://writer.example.com";

    @Test
    void upsertGeneratesAndPrintsUuidAndReadsTokenFromStdin() {
        OsiwsMaintenanceService service = mock(OsiwsMaintenanceService.class);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        commands(service, "stdin-token\n").upsert("job.yaml", "ids.csv", null, BASE_URL, null, false, context(stdout));

        ArgumentCaptor<UUID> jobId = ArgumentCaptor.forClass(UUID.class);
        verify(service).submit(eq(OsiwsJobType.UPSERT), eq(Path.of("job.yaml")), eq(Path.of("ids.csv")),
                jobId.capture(), eq(BASE_URL), eq("stdin-token"), eq(false));
        assertThat(stdout.toString(StandardCharsets.UTF_8)).isEqualTo(jobId.getValue() + System.lineSeparator());
    }

    @Test
    void deleteUsesExplicitUuidAndOptionTokenWins() {
        OsiwsMaintenanceService service = mock(OsiwsMaintenanceService.class);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        commands(service, "stdin-token\n").delete("job.yaml", null, JOB_ID.toUpperCase(), BASE_URL, "option-token",
                true, context(stdout));

        verify(service).submit(OsiwsJobType.DELETE, Path.of("job.yaml"), null, UUID.fromString(JOB_ID), BASE_URL,
                "option-token", true);
        assertThat(stdout.toString(StandardCharsets.UTF_8)).isEqualTo(JOB_ID + System.lineSeparator());
    }

    @Test
    void failedSubmissionDoesNotPrintTheJobIdToStdout() {
        OsiwsMaintenanceService service = mock(OsiwsMaintenanceService.class);
        doThrow(new OsiwsException("refused")).when(service).submit(
                eq(OsiwsJobType.UPSERT), any(), any(), any(UUID.class), eq(BASE_URL), eq("token"), eq(false));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        assertThatThrownBy(() -> commands(service, "token")
                .upsert("job.yaml", null, null, BASE_URL, null, false, context(stdout)))
                .isInstanceOf(OsiwsException.class)
                .hasMessage("refused");
        assertThat(stdout.size()).isZero();
    }

    @Test
    void reportWritesExactYamlToStdoutAndPassesPageSize() {
        OsiwsMaintenanceService service = mock(OsiwsMaintenanceService.class);
        String report = "job-state: open\n";
        when(service.report(OsiwsJobType.DELETE, UUID.fromString(JOB_ID), BASE_URL, 500, null, "token", false))
                .thenReturn(report);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        commands(service, "token").report("delete", JOB_ID, BASE_URL, null, "500", null, false, context(stdout));

        assertThat(stdout.toString(StandardCharsets.UTF_8)).isEqualTo(report);
    }

    @Test
    void reportWithOutputFileDoesNotWriteToStdout() {
        OsiwsMaintenanceService service = mock(OsiwsMaintenanceService.class);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        commands(service, "token").report("upsert", JOB_ID, BASE_URL, "report.yaml", null, null, false, context(stdout));

        verify(service).report(OsiwsJobType.UPSERT, UUID.fromString(JOB_ID), BASE_URL, null, Path.of("report.yaml"),
                "token", false);
        assertThat(stdout.size()).isZero();
    }

    @Test
    void invalidArgumentsAreRejectedBeforeCallingService() {
        OsiwsMaintenanceService service = mock(OsiwsMaintenanceService.class);
        OsiwsCommands commands = commands(service, "token");
        CommandContext context = context(new ByteArrayOutputStream());

        assertThatThrownBy(() -> commands.upsert("job.yaml", null, "not-a-uuid", BASE_URL, null, false, context))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Invalid job ID");
        assertThatThrownBy(() -> commands.report("rollback", JOB_ID, BASE_URL, null, null, null, false, context))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Expected upsert or delete");
        assertThatThrownBy(() -> commands.report("upsert", JOB_ID, BASE_URL, null, "many", null, false, context))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Invalid page size 'many'");
        assertThatThrownBy(() -> commands(service, "\n").delete("job.yaml", null, JOB_ID, BASE_URL, null, false, context))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("pipe the token to stdin");
        verifyNoInteractions(service);
    }

    @Test
    void unknownOptionIsRejectedBeforeCallingService() {
        OsiwsMaintenanceService service = mock(OsiwsMaintenanceService.class);
        CommandContext context = context(new ByteArrayOutputStream());
        when(context.parsedInput()).thenReturn(new ParsedInput("osiws", List.of("upsert"), List.of(
                CommandOption.with().longName("references-csv").value("ids.csv").build()), List.of()));

        assertThatThrownBy(() -> commands(service, "token")
                .upsert("job.yaml", null, null, BASE_URL, null, false, context))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Unknown option '--references-csv'");
        verifyNoInteractions(service);
    }

    private OsiwsCommands commands(OsiwsMaintenanceService service, String stdin) {
        return new OsiwsCommands(service, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
    }

    private CommandContext context(ByteArrayOutputStream stdout) {
        CommandContext context = mock(CommandContext.class);
        when(context.outputWriter()).thenReturn(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        when(context.parsedInput()).thenReturn(new ParsedInput("osiws", List.of(), List.of(), List.of()));
        return context;
    }
}
