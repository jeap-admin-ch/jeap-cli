package ch.admin.bit.jeap.cli;

import ch.admin.bit.jeap.cli.backfill.PasBackfillService;
import ch.admin.bit.jeap.cli.migration.process.Java25Migration;
import ch.admin.bit.jeap.cli.migration.process.SpringBoot4Migration;
import ch.admin.bit.jeap.cli.pcs.PcsMaintenanceService;
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

    @MockitoBean
    PasBackfillService pasBackfillService;

    @MockitoBean
    PcsMaintenanceService pcsMaintenanceService;

    @Test
    void test() throws Exception {
        ShellScreen screen = client.sendCommand("help");

        ShellAssertions.assertThat(screen)
                .containsText("migrate java-25")
                .containsText("migrate spring-boot-4")
                .containsText("pas-backfill send")
                .containsText("pas-backfill report")
                .containsText("PAS Backfill")
                .containsText("pcs reevaluate-relations")
                .containsText("pcs backfill")
                .containsText("pcs notify-relations")
                .containsText("pcs report")
                .containsText("PCS Maintenance")
                .containsText("AVAILABLE COMMANDS");
    }

    @Test
    void pasBackfillSendHelpShowsUsageInformation() throws Exception {
        ShellScreen screen = client.sendCommand("help pas-backfill send");

        ShellAssertions.assertThat(screen)
                .containsText("pas-backfill send")
                .containsText("Submit a backfill job YAML to the PAS")
                .containsText("--file")
                .containsText("--references-csv")
                .containsText("--job-id")
                .containsText("--url")
                .containsText("--access-token");
    }

    @Test
    void pasBackfillReportHelpShowsUsageInformation() throws Exception {
        ShellScreen screen = client.sendCommand("help pas-backfill report");

        ShellAssertions.assertThat(screen)
                .containsText("pas-backfill report")
                .containsText("Read the backfill job report from the PAS")
                .containsText("--job-id")
                .containsText("--url")
                .containsText("--output")
                .containsText("--access-token");
    }

    @Test
    void pcsSubmissionHelpShowsInputsAndWriteRole() throws Exception {
        ShellScreen reevaluation = client.sendCommand("help pcs reevaluate-relations");
        ShellAssertions.assertThat(reevaluation)
                .containsText("processcontextjob:write")
                .containsText("--file")
                .containsText("--processes-csv")
                .containsText("--job-id")
                .containsText("--url")
                .containsText("--access-token")
                .containsText("--allow-insecure-http");

        ShellScreen backfill = client.sendCommand("help pcs backfill");
        ShellAssertions.assertThat(backfill)
                .containsText("processcontextjob:write")
                .containsText("--process-data-csv");

        ShellScreen publication = client.sendCommand("help pcs notify-relations");
        ShellAssertions.assertThat(publication)
                .containsText("processcontextjob:write")
                .containsText("--file")
                .containsText("--relations-csv");
    }

    @Test
    void pcsReportHelpShowsTypesOutputAndReadRole() throws Exception {
        ShellScreen screen = client.sendCommand("help pcs report");

        ShellAssertions.assertThat(screen)
                .containsText("processcontextjob:read")
                .containsText("--job-type")
                .containsText("reevaluation, backfill or relation-publication")
                .containsText("--job-id")
                .containsText("--output")
                .containsText("--url")
                .containsText("--access-token")
                .containsText("--allow-insecure-http");
    }
}
