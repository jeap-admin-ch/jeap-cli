package ch.admin.bit.jeap.cli.backfill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
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
        service = new PasBackfillService(restClientBuilder, new BackfillReferenceCsvParser());
    }

    @Test
    void sendWithYamlReferencesPutsCompleteJobWithBearerToken() throws Exception {
        Path yamlFile = tempDir.resolve("backfill-job.yaml");
        String yaml = """
                message: TestEvent
                topic: test-topic
                num-of-retry: 1
                archiveDataReferences:
                  - id: DOC-2024-001
                    version: 1
                """;
        Files.writeString(yamlFile, yaml);
        server.expect(once(), requestTo(JOB_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(content().contentType(APPLICATION_YAML))
                .andExpect(content().string(allOf(
                        containsString("message: \"TestEvent\""),
                        containsString("topic: \"test-topic\""),
                        containsString("num-of-retry: 1"),
                        containsString("archiveDataReferences:"),
                        containsString("id: \"DOC-2024-001\""),
                        containsString("version: 1"))))
                .andRespond(withStatus(HttpStatus.CREATED));

        String result = service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully. 1 references submitted.");
        server.verify();
    }

    @Test
    void sendWithReferencesCsvMergesReferencesIntoYamlMetadata() throws Exception {
        Path yamlFile = tempDir.resolve("backfill-job.yaml");
        Files.writeString(yamlFile, """
                message: TestEvent
                topic: test-topic
                num-of-retry: 1
                """);
        Path csvFile = tempDir.resolve("references.csv");
        Files.writeString(csvFile, """
                id,version
                DOC-2024-001,1
                DOC-2024-002,2
                """);
        server.expect(once(), requestTo(JOB_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().contentType(APPLICATION_YAML))
                .andExpect(content().string(allOf(
                        containsString("message: \"TestEvent\""),
                        containsString("topic: \"test-topic\""),
                        containsString("id: \"DOC-2024-001\""),
                        containsString("version: 1"),
                        containsString("id: \"DOC-2024-002\""),
                        containsString("version: 2"))))
                .andRespond(withStatus(HttpStatus.CREATED));

        String result = service.send(yamlFile, csvFile, JOB_ID, BASE_URL, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully. 2 references submitted.");
        server.verify();
    }

    @Test
    void sendWithHundredCsvReferencesSubmitsHundredReferences() throws Exception {
        Path yamlFile = tempDir.resolve("backfill-job.yaml");
        Files.writeString(yamlFile, """
                message: TestEvent
                topic: test-topic
                """);
        Path csvFile = tempDir.resolve("references.csv");
        StringBuilder csv = new StringBuilder("id,version\n");
        for (int index = 1; index <= 100; index++) {
            csv.append("DOC-").append("%03d".formatted(index)).append(",").append(index).append("\n");
        }
        Files.writeString(csvFile, csv);
        server.expect(once(), requestTo(JOB_URL))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(Pattern.compile("id: ").matcher(body).results()).hasSize(100);
                    assertThat(body).contains("id: \"DOC-001\"", "version: 1", "id: \"DOC-100\"", "version: 100");
                })
                .andRespond(withStatus(HttpStatus.CREATED));

        String result = service.send(yamlFile, csvFile, JOB_ID, BASE_URL, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully. 100 references submitted.");
        server.verify();
    }

    @Test
    void sendFailsWhenYamlAndCsvReferencesAreProvided() throws Exception {
        Path csvFile = tempDir.resolve("references.csv");
        Files.writeString(csvFile, """
                id,version
                DOC-2024-001,1
                """);

        assertThatThrownBy(() -> service.send(yamlFile(), csvFile, JOB_ID, BASE_URL, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("Error: archiveDataReferences defined in both YAML and --references-csv. Use one source only.");
    }

    @Test
    void sendFailsWhenNoReferencesAreProvided() throws Exception {
        Path yamlFile = tempDir.resolve("backfill-job.yaml");
        Files.writeString(yamlFile, """
                message: TestEvent
                topic: test-topic
                """);

        assertThatThrownBy(() -> service.send(yamlFile, null, JOB_ID, BASE_URL, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("Error: No archiveDataReferences provided. Define them in the YAML file or use --references-csv.");
    }

    @Test
    void sendWrapsInvalidYamlReferenceVersion() throws Exception {
        Path yamlFile = tempDir.resolve("backfill-job.yaml");
        Files.writeString(yamlFile, """
                message: TestEvent
                topic: test-topic
                archiveDataReferences:
                  - id: DOC-2024-001
                    version: abc
                """);

        assertThatThrownBy(() -> service.send(yamlFile, null, JOB_ID, BASE_URL, ACCESS_TOKEN))
                .isInstanceOf(PasBackfillException.class)
                .hasMessage("Invalid YAML value 'abc' for archiveDataReferences[].version. Must be an integer.");
    }

    @Test
    void sendAcceptsOkResponse() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.OK));

        String result = service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully. 1 references submitted.");
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

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully. 1 references submitted.");
        server.verify();
    }

    @Test
    void sendSucceedsWhenServerRespondsWithBodyOn201() throws Exception {
        Path yamlFile = yamlFile();
        server.expect(once(), requestTo(JOB_URL))
                .andRespond(withStatus(HttpStatus.CREATED).body("{\"jobId\":\"" + JOB_ID + "\"}"));

        String result = service.send(yamlFile, JOB_ID, BASE_URL, ACCESS_TOKEN);

        assertThat(result).isEqualTo("Backfill job " + JOB_ID + " created successfully. 1 references submitted.");
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
        Files.writeString(yamlFile, """
                message: TestEvent
                topic: test-topic
                archiveDataReferences:
                  - id: DOC-2024-001
                    version: 1
                """);
        return yamlFile;
    }
}
