package ch.admin.bit.jeap.cli.osiws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class OsiwsMaintenanceServiceTest {

    private static final UUID JOB_ID = UUID.fromString("88dbb65f-9634-4685-bc86-17b72d715d3e");
    private static final String BASE_URL = "https://writer.example.com/index-writer";
    private static final String UPSERT_ENDPOINT = BASE_URL + "/api/upsert-jobs/" + JOB_ID;
    private static final String DELETE_ENDPOINT = BASE_URL + "/api/delete-jobs/" + JOB_ID;
    private static final String TOKEN = "secret-token";
    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private MockRestServiceServer server;
    private OsiwsMaintenanceService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        OsiwsConsole console = new OsiwsConsole(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        service = new OsiwsMaintenanceService(builder, new OsiwsRequestFactory(new OsiwsReferenceCsvParser()), console);
    }

    @Test
    void submitsUpsertJobAndNamesTheTargetSelectedByTheIndexWriter() throws Exception {
        server.expect(once(), requestTo(UPSERT_ENDPOINT))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header(HttpHeaders.ACCEPT, "application/yaml"))
                .andExpect(content().contentType("application/yaml;charset=UTF-8"))
                .andExpect(content().string(fixture("upsert-request.yaml")))
                .andRespond(withStatus(HttpStatus.OK).contentType(APPLICATION_YAML)
                        .body(fixture("upsert-report-page-1.yaml")));

        service.submit(OsiwsJobType.UPSERT, write("job.yaml", fixture("upsert-request.yaml")), null, JOB_ID,
                BASE_URL, TOKEN, false);

        server.verify();
        assertThat(stderr()).isEqualTo("""
                Submitting upsert job %1$s with 2 references to https://writer.example.com.
                Upsert job %1$s accepted with 2 references: schema major 1 (explicit) in \
                jme_decree_document_v1-000001, job-state completed.
                """.formatted(JOB_ID));
    }

    @Test
    void submitsDeleteJobAndNamesAllSelectedMajors() throws Exception {
        server.expect(once(), requestTo(DELETE_ENDPOINT))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(fixture("delete-request.yaml")))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("delete-report-page.yaml")));

        service.submit(OsiwsJobType.DELETE, write("job.yaml", "indexType: DecreeDocument\nindexMajorVersion: 1\n"),
                write("ids.csv", "id\n12345\n67890\n"), JOB_ID, BASE_URL, TOKEN, false);

        server.verify();
        assertThat(stderr()).contains("Delete job " + JOB_ID
                + " accepted with 2 references: schema majors 1, 2 (all), job-state open.");
    }

    @Test
    void nonAsciiIdsAreSentAsUtf8() throws Exception {
        String expectedRequest = """
                indexType: DecreeDocument
                originReferences:
                  - id: Zürich-Ž1
                  - id: €-42
                """;
        server.expect(once(), requestTo(DELETE_ENDPOINT))
                .andExpect(content().bytes(expectedRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(content().contentType("application/yaml;charset=UTF-8"))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("delete-report-page.yaml")));

        service.submit(OsiwsJobType.DELETE, write("job.yaml", "indexType: DecreeDocument\n"),
                write("ids.csv", "id\nZürich-Ž1\n€-42\n"), JOB_ID, BASE_URL, TOKEN, false);

        server.verify();
    }

    @Test
    void invalidInputIsRejectedBeforeContactingTheIndexWriter() throws Exception {
        Path yaml = write("job.yaml", "indexType: DecreeDocument\n");
        Path csv = write("ids.csv", "id\n12345\n12345\n");

        assertThatThrownBy(() -> service.submit(OsiwsJobType.DELETE, yaml, csv, JOB_ID, BASE_URL, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Duplicate id '12345'");
        server.verify();
        assertThat(stderr()).isEmpty();
    }

    @Test
    void refusedTargetShowsCodeAndReasonWithoutTryingAnotherMajor() throws Exception {
        server.expect(once(), requestTo(UPSERT_ENDPOINT))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(APPLICATION_YAML).body("""
                        code: NOT_ALLOWED
                        message: Index type DecreeDocument major 1 uses rollover policy jme-rollover
                        """));

        assertThatThrownBy(() -> service.submit(OsiwsJobType.UPSERT, upsertRequest(), JOB_ID, BASE_URL, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessage("The index writer refused the target of the upsert job " + JOB_ID + " (NOT_ALLOWED): "
                        + "Index type DecreeDocument major 1 uses rollover policy jme-rollover. Nothing was stored.");
        server.verify();
    }

    @Test
    void mapsHttpFailuresToActionableMessages() throws Exception {
        assertSubmissionFailure(HttpStatus.BAD_REQUEST, "code: INVALID_REQUEST\nmessage: Unknown schema major 3\n",
                "rejected the upsert job " + JOB_ID + " request (INVALID_REQUEST): Unknown schema major 3");
        assertSubmissionFailure(HttpStatus.UNAUTHORIZED, "", "valid, non-expired token");
        assertSubmissionFailure(HttpStatus.FORBIDDEN, "", "needs role upsertjob#create");
        assertSubmissionFailure(HttpStatus.NOT_FOUND, "{\"status\":404,\"error\":\"Not Found\"}",
                "has no maintenance job API", "jeap.opensearch.indexwriter.maintenance.enabled");
        assertSubmissionFailure(HttpStatus.CONFLICT, "code: JOB_CONFLICT\nmessage: changed reference version\n",
                "already exists with different content (JOB_CONFLICT): changed reference version");
        assertSubmissionFailure(HttpStatus.SERVICE_UNAVAILABLE, "code: STORAGE_UNAVAILABLE\nmessage: retry later\n",
                "temporarily unavailable (STORAGE_UNAVAILABLE)", "Retry later with --job-id=" + JOB_ID);
        assertSubmissionFailure(HttpStatus.BAD_GATEWAY, "proxy error",
                "HTTP status 502: proxy error", "--job-id=" + JOB_ID);
    }

    @Test
    void uncertainSubmissionNamesTheJobIdForASafeRetry() throws Exception {
        server.expect(requestTo(UPSERT_ENDPOINT)).andRespond(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> service.submit(OsiwsJobType.UPSERT, upsertRequest(), JOB_ID, BASE_URL, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Read timed out")
                .hasMessageContaining("may or may not have been stored")
                .hasMessageContaining("--job-id=" + JOB_ID + "; an identical retry is safe")
                .hasMessageNotContaining(TOKEN);
        assertThat(stderr()).contains("Submitting upsert job " + JOB_ID);
        server.verify();
    }

    @Test
    void tokenEchoedByErrorResponseIsRedacted() throws Exception {
        server.expect(requestTo(UPSERT_ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("rejected token " + TOKEN));

        assertThatThrownBy(() -> service.submit(OsiwsJobType.UPSERT, upsertRequest(), JOB_ID, BASE_URL, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("[REDACTED]")
                .hasMessageNotContaining(TOKEN);
        server.verify();
    }

    @Test
    void reportReadsEveryPageAndExportsOneDocument() throws Exception {
        server.expect(once(), requestTo(UPSERT_ENDPOINT + "?limit=2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header(HttpHeaders.ACCEPT, "application/yaml"))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("upsert-report-page-1.yaml")));
        server.expect(once(), requestTo(UPSERT_ENDPOINT + "?limit=2&cursor=2"))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("upsert-report-page-2.yaml")));

        String report = service.report(OsiwsJobType.UPSERT, JOB_ID, BASE_URL, 2, null, TOKEN, false);

        server.verify();
        assertThat(report).isEqualTo(fixture("upsert-report.yaml"));
        assertThat(stderr()).contains("Read 3 references of upsert job " + JOB_ID + " in 2 pages.");
    }

    @Test
    void singlePageReportUsesTheIndexWriterDefaultPageSizeAndDropsThePageBlock() throws Exception {
        server.expect(once(), requestTo(DELETE_ENDPOINT))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("delete-report-page.yaml")));

        String report = service.report(OsiwsJobType.DELETE, JOB_ID, BASE_URL, null, null, TOKEN, false);

        server.verify();
        assertThat(report)
                .contains("target-selection: all")
                .contains("id: \"67890\"")
                .doesNotContain("page:")
                .doesNotContain("next-cursor");
        assertThat(stderr()).isEmpty();
    }

    @Test
    void reportIsWrittenToTheOutputFile() throws Exception {
        Path output = tempDir.resolve("report.yaml");
        server.expect(requestTo(UPSERT_ENDPOINT + "?limit=2"))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("upsert-report-page-1.yaml")));
        server.expect(requestTo(UPSERT_ENDPOINT + "?limit=2&cursor=2"))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("upsert-report-page-2.yaml")));

        assertThat(service.report(OsiwsJobType.UPSERT, JOB_ID, BASE_URL, 2, output, TOKEN, false)).isNull();

        assertThat(output).hasContent(fixture("upsert-report.yaml"));
        server.verify();
    }

    @Test
    void laterPageFailureWritesNothingAndExitsUnsuccessfully() throws Exception {
        Path output = tempDir.resolve("report.yaml");
        server.expect(requestTo(UPSERT_ENDPOINT + "?limit=2"))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("upsert-report-page-1.yaml")));
        server.expect(requestTo(UPSERT_ENDPOINT + "?limit=2&cursor=2"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("code: STORAGE_UNAVAILABLE\nmessage: The job storage is temporarily unavailable\n"));

        assertThatThrownBy(() -> service.report(OsiwsJobType.UPSERT, JOB_ID, BASE_URL, 2, output, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageStartingWith("Report export incomplete, nothing was written: page 2 failed after 2 of 3 "
                        + "references were read.")
                .hasMessageContaining("STORAGE_UNAVAILABLE")
                .hasMessageNotContaining("--job-id");
        assertThat(output).doesNotExist();
        server.verify();
    }

    @Test
    void pageThatDoesNotAdvanceIsRejected() throws Exception {
        String page = fixture("upsert-report-page-1.yaml").replace("total-references: 3", "total-references: 10");
        server.expect(requestTo(UPSERT_ENDPOINT + "?limit=2"))
                .andRespond(withStatus(HttpStatus.OK).body(page));
        server.expect(requestTo(UPSERT_ENDPOINT + "?limit=2&cursor=2"))
                .andRespond(withStatus(HttpStatus.OK).body(page));

        assertThatThrownBy(() -> service.report(OsiwsJobType.UPSERT, JOB_ID, BASE_URL, 2, null, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Report export incomplete")
                .hasMessageContaining("the next-cursor '2' does not advance");
        server.verify();
    }

    @Test
    void pagesOfAChangingJobAreRejected() throws Exception {
        server.expect(requestTo(UPSERT_ENDPOINT + "?limit=2"))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("upsert-report-page-1.yaml")));
        server.expect(requestTo(UPSERT_ENDPOINT + "?limit=2&cursor=2"))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("upsert-report-page-2.yaml")
                        .replace("total-references: 3", "total-references: 5")));

        assertThatThrownBy(() -> service.report(OsiwsJobType.UPSERT, JOB_ID, BASE_URL, 2, null, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Report export incomplete")
                .hasMessageContaining("total-references changed from 3 to 5");
        server.verify();
    }

    @Test
    void reportMissingReferencesIsIncomplete() throws Exception {
        server.expect(requestTo(UPSERT_ENDPOINT))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("upsert-report-page-1.yaml")
                        .replace("next-cursor: '2'", "next-cursor: null")));

        assertThatThrownBy(() -> service.report(OsiwsJobType.UPSERT, JOB_ID, BASE_URL, null, null, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessage("Report export incomplete, nothing was written. The report is incomplete: the index "
                        + "writer returned 2 of 3 references.");
        server.verify();
    }

    @Test
    void reportOfAnotherJobIsRejected() throws Exception {
        server.expect(requestTo(DELETE_ENDPOINT))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("delete-report-page.yaml")
                        .replace(JOB_ID.toString(), "970774be-7980-4748-a79c-d477a75f9270")));

        assertThatThrownBy(() -> service.report(OsiwsJobType.DELETE, JOB_ID, BASE_URL, null, null, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("inconsistent report page")
                .hasMessageContaining("970774be-7980-4748-a79c-d477a75f9270");
        server.verify();
    }

    @Test
    void reportFailuresNameJobTypeAndReadRole() throws Exception {
        server.expect(requestTo(DELETE_ENDPOINT)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("code: JOB_NOT_FOUND\nmessage: Job " + JOB_ID + " not found\n"));
        assertThatThrownBy(() -> service.report(OsiwsJobType.DELETE, JOB_ID, BASE_URL, null, null, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessage("The delete job " + JOB_ID + " was not found. Check the job ID and --job-type.");
        server.verify();
        server.reset();

        server.expect(requestTo(DELETE_ENDPOINT)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.report(OsiwsJobType.DELETE, JOB_ID, BASE_URL, null, null, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("needs role jobreport#read");
        server.verify();
    }

    @Test
    void invalidPageSizeAndOutputPathAreRejectedBeforeRequests() {
        assertThatThrownBy(() -> service.report(OsiwsJobType.UPSERT, JOB_ID, BASE_URL, 0, null, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Invalid page size");
        assertThatThrownBy(() -> service.report(OsiwsJobType.UPSERT, JOB_ID, BASE_URL, null, tempDir, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("is a directory");
        server.verify();
    }

    @Test
    void urlsMustBeHttpsUnlessExplicitlyLoopback() throws Exception {
        PreparedOsiwsRequest request = upsertRequest();

        assertThatThrownBy(() -> service.submit(OsiwsJobType.UPSERT, request, JOB_ID, "file:///tmp/writer", TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Provide an HTTPS URL");
        assertThatThrownBy(() -> service.submit(OsiwsJobType.UPSERT, request, JOB_ID, "http://localhost:8080", TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("--allow-insecure-http=true");
        assertThatThrownBy(() -> service.submit(OsiwsJobType.UPSERT, request, JOB_ID, "http://writer.example.com", TOKEN, true))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("allowed only for localhost");
        server.verify();
    }

    @Test
    void invalidUrlsAreRejectedWithoutEchoingCredentialsOrQuery() {
        PreparedOsiwsRequest request = new PreparedOsiwsRequest("indexType: DecreeDocument\n", 1);
        assertUrlRejected(request, "https://user:secret@writer.example.com", "User information is not allowed");
        assertUrlRejected(request, "https://user:secret@writer.example.com/?x=1", "User information is not allowed");
        assertUrlRejected(request, "ftp://user:secret@writer.example.com", "User information is not allowed");
        // underscores are not valid in host names, so URI parses no host and no user info, only the raw authority
        assertUrlRejected(request, "https://user:secret@writer_example.com", "User information is not allowed");
        assertUrlRejected(request, "https://writer.example.com/base?token=secret", "Query parameters and fragments");
        assertUrlRejected(request, "https://writer.example.com/base#secret", "Query parameters and fragments");
        assertUrlRejected(request, "ftp://writer.example.com/secret", "Provide an HTTPS URL");
        assertUrlRejected(request, "https://writer.example.com/ secret", "Provide an HTTPS URL");

        assertThatThrownBy(() -> service.report(OsiwsJobType.UPSERT, JOB_ID, "https://user:secret@writer.example.com",
                null, null, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageNotContaining("secret");
        server.verify();
        assertThat(stderr()).doesNotContain("secret");
    }

    private void assertUrlRejected(PreparedOsiwsRequest request, String url, String expectedMessage) {
        assertThatThrownBy(() -> service.submit(OsiwsJobType.UPSERT, request, JOB_ID, url, TOKEN, false))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining(expectedMessage)
                .hasMessageNotContaining("secret")
                .hasNoCause();
    }

    @Test
    void loopbackHttpAndBasePathsWithTrailingSlashAreSupported() throws Exception {
        server.expect(requestTo("http://127.0.0.1:8080/root%20context/api/upsert-jobs/" + JOB_ID))
                .andRespond(withStatus(HttpStatus.OK).body(fixture("upsert-report-page-1.yaml")));

        service.submit(OsiwsJobType.UPSERT, upsertRequest(), JOB_ID, "http://127.0.0.1:8080/root%20context/", TOKEN, true);

        server.verify();
    }

    private void assertSubmissionFailure(HttpStatus status, String body, String... expectedParts) throws Exception {
        server.expect(requestTo(UPSERT_ENDPOINT)).andRespond(withStatus(status).body(body));

        var assertion = assertThatThrownBy(
                () -> service.submit(OsiwsJobType.UPSERT, upsertRequest(), JOB_ID, BASE_URL, TOKEN, false))
                .isInstanceOf(OsiwsException.class);
        for (String expectedPart : expectedParts) {
            assertion.hasMessageContaining(expectedPart);
        }
        server.verify();
        server.reset();
    }

    private PreparedOsiwsRequest upsertRequest() throws Exception {
        return new PreparedOsiwsRequest(fixture("upsert-request.yaml"), 2);
    }

    private String stderr() {
        return stderr.toString(StandardCharsets.UTF_8);
    }

    private Path write(String name, String content) throws Exception {
        return Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private String fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/osiws/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
