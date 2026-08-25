package ch.admin.bit.jeap.cli.commands;

import ch.admin.bit.jeap.cli.pcs.PcsException;
import ch.admin.bit.jeap.cli.pcs.PcsJobType;
import ch.admin.bit.jeap.cli.pcs.PcsMaintenanceService;
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
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

class PcsCommandsTest {

    private static final String JOB_ID = "88dbb65f-9634-4685-bc86-17b72d715d3e";
    private static final String BASE_URL = "https://pcs.example.com";

    @Test
    void backfillGeneratesAndPrintsUuidAndReadsTokenFromStdin() {
        PcsMaintenanceService service = mock(PcsMaintenanceService.class);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PcsCommands commands = commands(service, "stdin-token\n");

        commands.backfill("job.yaml", "data.csv", null, BASE_URL, null, false, context(stdout));

        ArgumentCaptor<UUID> jobId = ArgumentCaptor.forClass(UUID.class);
        verify(service).submitBackfill(
                eq(Path.of("job.yaml")), eq(Path.of("data.csv")), jobId.capture(), eq(BASE_URL), eq("stdin-token"),
                eq(false));
        assertThat(UUID.fromString(stdout.toString(StandardCharsets.UTF_8).trim())).isEqualTo(jobId.getValue());
        assertThat(stdout.toString(StandardCharsets.UTF_8)).doesNotContain("stdin-token");
    }

    @Test
    void reevaluationUsesExplicitUuidUnchangedAndOptionTokenWins() {
        PcsMaintenanceService service = mock(PcsMaintenanceService.class);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PcsCommands commands = commands(service, "stdin-token\n");

        commands.reevaluateRelations("job.yaml", null, JOB_ID, BASE_URL, "option-token", false, context(stdout));

        verify(service).submitReevaluation(
                Path.of("job.yaml"), null, UUID.fromString(JOB_ID), BASE_URL, "option-token", false);
        assertThat(stdout.toString(StandardCharsets.UTF_8)).isEqualTo(JOB_ID + System.lineSeparator());
    }

    @Test
    void relationPublicationAcceptsCsvWithoutYamlFile() {
        PcsMaintenanceService service = mock(PcsMaintenanceService.class);
        PcsCommands commands = commands(service, "token");

        commands.notifyRelations(
                null, "relations.csv", JOB_ID, BASE_URL, null, true, context(new ByteArrayOutputStream()));

        verify(service).submitRelationPublication(
                null, Path.of("relations.csv"), UUID.fromString(JOB_ID), BASE_URL, "token", true);
    }

    @Test
    void reportWritesExactYamlToStdoutWithoutAnExtraNewline() {
        PcsMaintenanceService service = mock(PcsMaintenanceService.class);
        String report = "job-state: open\n";
        when(service.report(
                PcsJobType.REEVALUATION, UUID.fromString(JOB_ID), BASE_URL, null, "token", false))
                .thenReturn(report);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PcsCommands commands = commands(service, "token");

        commands.report("reevaluation", JOB_ID, BASE_URL, null, null, false, context(stdout));

        assertThat(stdout.toString(StandardCharsets.UTF_8)).isEqualTo(report);
    }

    @Test
    void reportWithOutputFileDoesNotWriteStatusToStdout() {
        PcsMaintenanceService service = mock(PcsMaintenanceService.class);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PcsCommands commands = commands(service, "token");

        commands.report("relation-publication", JOB_ID, BASE_URL, "report.yaml", null, false, context(stdout));

        verify(service).report(PcsJobType.RELATION_PUBLICATION, UUID.fromString(JOB_ID), BASE_URL,
                Path.of("report.yaml"), "token", false);
        assertThat(stdout.size()).isZero();
    }

    @Test
    void invalidUuidIsRejectedBeforeCallingService() {
        PcsMaintenanceService service = mock(PcsMaintenanceService.class);
        PcsCommands commands = commands(service, "token");

        assertThatThrownBy(() -> commands.backfill(
                "job.yaml", null, "not-a-uuid", BASE_URL, null, false, context(new ByteArrayOutputStream())))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("Invalid job ID");
        verifyNoInteractions(service);
    }

    @Test
    void missingTokenIsRejectedBeforeCallingService() {
        PcsMaintenanceService service = mock(PcsMaintenanceService.class);
        PcsCommands commands = commands(service, "\n");

        assertThatThrownBy(() -> commands.backfill(
                "job.yaml", null, JOB_ID, BASE_URL, null, false, context(new ByteArrayOutputStream())))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("pipe the token to stdin");
        verify(service, never()).submitBackfill(
                Path.of("job.yaml"), null, UUID.fromString(JOB_ID), BASE_URL, "", false);
    }

    @Test
    void unknownOptionIsRejectedBeforeCallingService() {
        PcsMaintenanceService service = mock(PcsMaintenanceService.class);
        PcsCommands commands = commands(service, "token");
        CommandContext context = context(new ByteArrayOutputStream());
        when(context.parsedInput()).thenReturn(new ParsedInput("pcs", List.of("backfill"), List.of(
                CommandOption.with().longName("job-idd").value(JOB_ID).build()), List.of()));

        assertThatThrownBy(() -> commands.backfill("job.yaml", null, null, BASE_URL, null, false, context))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("Unknown option '--job-idd'");
        verifyNoInteractions(service);
    }

    @Test
    void generatedUuidRemainsVisibleOnSubmissionFailureWithoutPollutingStdout() {
        PcsMaintenanceService service = mock(PcsMaintenanceService.class);
        doThrow(new PcsException("connection failed")).when(service).submitBackfill(
                eq(Path.of("job.yaml")), eq(null), any(UUID.class), eq(BASE_URL), eq("token"), eq(false));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PcsCommands commands = commands(service, "token");

        assertThatThrownBy(() -> commands.backfill(
                "job.yaml", null, null, BASE_URL, null, false, context(stdout)))
                .isInstanceOf(PcsException.class)
                .hasMessageMatching("Submission failed for generated job ID [0-9a-f-]{36}: connection failed");
        assertThat(stdout.size()).isZero();
    }

    private PcsCommands commands(PcsMaintenanceService service, String stdin) {
        return new PcsCommands(service,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
    }

    private CommandContext context(ByteArrayOutputStream stdout) {
        CommandContext context = mock(CommandContext.class);
        when(context.outputWriter()).thenReturn(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        when(context.parsedInput()).thenReturn(new ParsedInput("pcs", List.of(), List.of(), List.of()));
        return context;
    }
}
