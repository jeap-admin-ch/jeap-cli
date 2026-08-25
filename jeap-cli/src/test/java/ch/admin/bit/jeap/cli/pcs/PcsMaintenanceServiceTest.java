package ch.admin.bit.jeap.cli.pcs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
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

class PcsMaintenanceServiceTest {

    private static final UUID JOB_ID = UUID.fromString("88dbb65f-9634-4685-bc86-17b72d715d3e");
    private static final String BASE_URL = "https://pcs.example.com/process-context";
    private static final String TOKEN = "secret-token";
    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");

    @TempDir
    Path tempDir;

    private MockRestServiceServer server;
    private PcsMaintenanceService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        PcsProperties properties = new PcsProperties();
        PcsRequestFactory requestFactory = new PcsRequestFactory(
                properties,
                new ReevaluationCsvParser(properties),
                new ProcessDataCsvParser(properties),
                new RelationCsvParser(properties));
        service = new PcsMaintenanceService(builder, requestFactory);
    }

    @Test
    void submitsEveryGoldenRequestToItsMatchingEndpoint() throws Exception {
        assertSubmission(
                PcsJobType.REEVALUATION,
                "reevaluation-request.yaml",
                "/api/reevaluation-jobs/" + JOB_ID);
        assertSubmission(
                PcsJobType.BACKFILL,
                "backfill-request.yaml",
                "/api/backfill-jobs/" + JOB_ID);
        assertSubmission(
                PcsJobType.RELATION_PUBLICATION,
                "relation-publication-request.yaml",
                "/api/relation-publication-jobs/" + JOB_ID);
    }

    @Test
    void reportMapsTypeDirectlyAndReturnsResponseUnchanged() throws Exception {
        for (PcsJobType type : PcsJobType.values()) {
            String report = fixture(type.endpointSegment() + "-report.yaml");
            server.expect(once(), requestTo(BASE_URL + "/api/" + type.endpointSegment() + "-jobs/" + JOB_ID))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                    .andExpect(header(HttpHeaders.ACCEPT, "application/yaml"))
                    .andRespond(withStatus(HttpStatus.OK).contentType(APPLICATION_YAML).body(report));

            assertThat(service.report(type, JOB_ID, BASE_URL, null, TOKEN, false)).isEqualTo(report);
            server.verify();
            server.reset();
        }
    }

    @Test
    void reportWritesResponseUnchangedToOutputFile() throws Exception {
        String report = fixture("backfill-report.yaml");
        Path output = tempDir.resolve("report.yaml");
        server.expect(requestTo(BASE_URL + "/api/backfill-jobs/" + JOB_ID))
                .andRespond(withStatus(HttpStatus.OK).body(report));

        assertThat(service.report(PcsJobType.BACKFILL, JOB_ID, BASE_URL, output, TOKEN, false)).isNull();
        assertThat(output).hasContent(report);
        server.verify();
    }

    @Test
    void mapsRelevantHttpFailuresToActionableMessages() throws Exception {
        assertHttpFailure(HttpStatus.BAD_REQUEST, "Invalid processTemplateName", "rejected", "Invalid processTemplateName");
        assertHttpFailure(HttpStatus.UNAUTHORIZED, "invalid token", "valid, non-expired token");
        assertHttpFailure(HttpStatus.FORBIDDEN, "", "processcontextjob:write");
        assertHttpFailure(HttpStatus.NOT_FOUND, "", "not found");
        assertHttpFailure(HttpStatus.CONFLICT, "different content", "different content", JOB_ID.toString());
        assertHttpFailure(HttpStatus.INTERNAL_SERVER_ERROR, "temporary failure", "HTTP status 500", "temporary failure");
    }

    @Test
    void reportForbiddenNamesReadRole() {
        server.expect(requestTo(BASE_URL + "/api/reevaluation-jobs/" + JOB_ID))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.report(PcsJobType.REEVALUATION, JOB_ID, BASE_URL, null, TOKEN, false))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("processcontextjob:read");
        server.verify();
    }

    @Test
    void connectivityFailureDoesNotExposeToken() throws Exception {
        PreparedPcsRequest request = new PreparedPcsRequest(fixture("reevaluation-request.yaml"), 2);
        server.expect(requestTo(BASE_URL + "/api/reevaluation-jobs/" + JOB_ID))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> service.submit(PcsJobType.REEVALUATION, request, JOB_ID, BASE_URL, TOKEN, false))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("Could not connect to PCS")
                .hasMessageNotContaining(TOKEN);
        server.verify();
    }

    @Test
    void invalidBaseUrlIsRejectedBeforeRequest() throws Exception {
        PreparedPcsRequest request = new PreparedPcsRequest(fixture("reevaluation-request.yaml"), 2);

        assertThatThrownBy(() -> service.submit(
                PcsJobType.REEVALUATION, request, JOB_ID, "file:///tmp/pcs", TOKEN, false))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("HTTPS PCS URL");
        server.verify();
    }

    @Test
    void insecureHttpIsRejectedByDefault() throws Exception {
        PreparedPcsRequest request = new PreparedPcsRequest(fixture("reevaluation-request.yaml"), 2);

        assertThatThrownBy(() -> service.submit(
                PcsJobType.REEVALUATION, request, JOB_ID, "http://localhost:8080/process-context", TOKEN, false))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("--allow-insecure-http=true");
        server.verify();
    }

    @Test
    void insecureHttpOptInIsRestrictedToLoopbackHosts() throws Exception {
        PreparedPcsRequest request = new PreparedPcsRequest(fixture("reevaluation-request.yaml"), 2);

        assertThatThrownBy(() -> service.submit(
                PcsJobType.REEVALUATION, request, JOB_ID, "http://pcs.example.com", TOKEN, true))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("allowed only for localhost");
        server.verify();
    }

    @Test
    void insecureHttpOptInAllowsLoopbackPcs() throws Exception {
        String baseUrl = "http://127.0.0.1:8080/process-context";
        PreparedPcsRequest request = new PreparedPcsRequest(fixture("reevaluation-request.yaml"), 2);
        server.expect(requestTo(baseUrl + "/api/reevaluation-jobs/" + JOB_ID))
                .andRespond(withStatus(HttpStatus.CREATED));

        service.submit(PcsJobType.REEVALUATION, request, JOB_ID, baseUrl, TOKEN, true);

        server.verify();
    }

    @Test
    void basePathAndTrailingSlashProduceOneCanonicalEndpointPath() throws Exception {
        String baseUrl = "https://pcs.example.com/root%20context/";
        PreparedPcsRequest request = new PreparedPcsRequest(fixture("reevaluation-request.yaml"), 2);
        server.expect(requestTo("https://pcs.example.com/root%20context/api/reevaluation-jobs/" + JOB_ID))
                .andRespond(withStatus(HttpStatus.OK));

        service.submit(PcsJobType.REEVALUATION, request, JOB_ID, baseUrl, TOKEN, false);

        server.verify();
    }

    @Test
    void urlUserInfoIsRejectedWithoutExposingIt() throws Exception {
        PreparedPcsRequest request = new PreparedPcsRequest(fixture("reevaluation-request.yaml"), 2);

        assertThatThrownBy(() -> service.submit(
                PcsJobType.REEVALUATION, request, JOB_ID, "https://user:password@pcs.example.com/base", TOKEN, false))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("User information is not allowed")
                .hasMessageNotContaining("password");
        server.verify();
    }

    @Test
    void tokenEchoedByErrorResponseIsRedacted() throws Exception {
        PreparedPcsRequest request = new PreparedPcsRequest(fixture("reevaluation-request.yaml"), 2);
        server.expect(requestTo(BASE_URL + "/api/reevaluation-jobs/" + JOB_ID))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("rejected token " + TOKEN));

        assertThatThrownBy(() -> service.submit(PcsJobType.REEVALUATION, request, JOB_ID, BASE_URL, TOKEN, false))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("[REDACTED]")
                .hasMessageNotContaining(TOKEN);
        server.verify();
    }

    @Test
    void invalidOutputPathIsRejectedBeforeReportRequest() {
        assertThatThrownBy(() -> service.report(
                PcsJobType.BACKFILL, JOB_ID, BASE_URL, tempDir, TOKEN, false))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("is a directory");
        server.verify();
    }

    private void assertSubmission(PcsJobType type, String fixtureName, String endpoint) throws Exception {
        String requestYaml = fixture(fixtureName);
        server.expect(once(), requestTo(BASE_URL + endpoint))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header(HttpHeaders.ACCEPT, "application/yaml"))
                .andExpect(content().contentType(APPLICATION_YAML))
                .andExpect(content().string(requestYaml))
                .andRespond(withStatus(HttpStatus.CREATED));

        service.submit(type, new PreparedPcsRequest(requestYaml, 2), JOB_ID, BASE_URL, TOKEN, false);
        server.verify();
        server.reset();
    }

    private void assertHttpFailure(HttpStatus status, String responseBody, String... expectedParts) throws Exception {
        PreparedPcsRequest request = new PreparedPcsRequest(fixture("reevaluation-request.yaml"), 2);
        server.expect(requestTo(BASE_URL + "/api/reevaluation-jobs/" + JOB_ID))
                .andRespond(withStatus(status).body(responseBody));

        var assertion = assertThatThrownBy(
                () -> service.submit(PcsJobType.REEVALUATION, request, JOB_ID, BASE_URL, TOKEN, false))
                .isInstanceOf(PcsException.class);
        for (String expectedPart : expectedParts) {
            assertion.hasMessageContaining(expectedPart);
        }
        server.verify();
        server.reset();
    }

    private String fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/pcs/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
