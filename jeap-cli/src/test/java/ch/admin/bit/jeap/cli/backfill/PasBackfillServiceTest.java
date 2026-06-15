package ch.admin.bit.jeap.cli.backfill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PasBackfillServiceTest {

    private static final String JOB_ID = "88dbb65f-9634-4685-bc86-17b72d715d3e";
    private static final String BASE_URL = "https://pas.example.com";
    private static final String ACCESS_TOKEN = "test-token";
    private static final String JOB_URL = BASE_URL + "/api/jobs/" + JOB_ID;
    private static final String REPORT_URL = JOB_URL + "/report";
    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");

    @TempDir
    Path tempDir;

    private MockRestServiceServer server;
    private PasBackfillService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        service = new PasBackfillService(restClientBuilder);
    }

    @Test
    void sendReadsYamlAndPutsJobWithBearerToken() throws Exception {
        Path yamlFile = tempDir.resolve("backfill-job.yaml");
        String yaml = "message: TestEvent\n";
        Files.writeString(yamlFile, yaml);
        server.expect(once(), requestTo(JOB_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(content().contentType(APPLICATION_YAML))
                .andExpect(content().string(yaml))
                .andRespond(withStatus(HttpStatus.CREATED));

        String result = service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully.");
        server.verify();
    }

    @Test
    void sendAcceptsOkResponse() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.OK));

        String result = service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully.");
        server.verify();
    }

    @Test
    void sendFailsForMissingFile() {
        Path missingFile = tempDir.resolve("missing.yaml");

        assertThatThrownBy(() -> service.send(missingFile, JOB_ID, BASE_URL, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("File not found: " + missingFile);
    }

    @Test
    void sendReportsConflict() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("Job " + JOB_ID + " already exists.");
        server.verify();
    }

    @Test
    void sendReportsValidationBody() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("invalid yaml"));

        assertThatThrownBy(() -> service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("invalid yaml");
        server.verify();
    }

    @Test
    void sendReportsOtherHttpStatus() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("PAS request failed with HTTP status 500");
        server.verify();
    }

    @Test
    void sendReportsOtherClientHttpStatus() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("PAS request failed with HTTP status 403");
        server.verify();
    }

    @Test
    void reportReturnsYamlFromStdoutResult() {
        String reportYaml = "state: FINISHED\n";
        server.expect(once(), requestTo(REPORT_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(header(HttpHeaders.ACCEPT, "application/yaml"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(APPLICATION_YAML)
                        .body(reportYaml));

        String result = service.report(JOB_ID, BASE_URL, null, ACCESS_TOKEN);

        assertThat(result).isEqualTo(reportYaml);
        server.verify();
    }

    @Test
    void reportWritesYamlToOutputFile() throws Exception {
        String reportYaml = "state: FINISHED\n";
        Path output = tempDir.resolve("backfill-report.yaml");
        server.expect(once(), requestTo(REPORT_URL))
                .andRespond(withStatus(HttpStatus.OK).body(reportYaml));

        String result = service.report(JOB_ID, BASE_URL, output, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Report written to " + output);
        assertThat(output).hasContent(reportYaml);
        server.verify();
    }

    @Test
    void reportReportsNotFound() {
        server.expect(once(), requestTo(REPORT_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.report(JOB_ID, BASE_URL, null, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("Job " + JOB_ID + " not found.");
        server.verify();
    }

    @Test
    void reportReportsOtherHttpStatus() {
        server.expect(once(), requestTo(REPORT_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.report(JOB_ID, BASE_URL, null, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("PAS request failed with HTTP status 500");
        server.verify();
    }

    @Test
    void reportReportsOtherClientHttpStatus() {
        server.expect(once(), requestTo(REPORT_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.report(JOB_ID, BASE_URL, null, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("PAS request failed with HTTP status 403");
        server.verify();
    }

    @Test
    void sendSucceedsWhenServerRespondsWithBodyOn200() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.OK).body("{\"jobId\":\"" + JOB_ID + "\"}"));

        String result = service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully.");
        server.verify();
    }

    @Test
    void sendSucceedsWhenServerRespondsWithBodyOn201() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.CREATED).body("{\"jobId\":\"" + JOB_ID + "\"}"));

        String result = service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully.");
        server.verify();
    }

    @Test
    void sendReportsConflictWhenServerRespondsWithBody() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("Job already exists with different content."));

        assertThatThrownBy(() -> service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("Job " + JOB_ID + " already exists.");
        server.verify();
    }

    @Test
    void reportReportsNotFoundWhenServerRespondsWithBody() {
        server.expect(once(), requestTo(REPORT_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("No such job."));

        assertThatThrownBy(() -> service.report(JOB_ID, BASE_URL, null, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("Job " + JOB_ID + " not found.");
        server.verify();
    }

    private Path yamlFile() throws Exception {
        Path yamlFile = tempDir.resolve("backfill-job.yaml");
        Files.writeString(yamlFile, "message: TestEvent\n");
        return yamlFile;
    }
}
