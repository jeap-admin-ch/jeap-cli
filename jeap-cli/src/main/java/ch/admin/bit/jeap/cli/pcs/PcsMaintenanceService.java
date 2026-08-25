package ch.admin.bit.jeap.cli.pcs;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PcsMaintenanceService {

    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");
    private static final int MAX_ERROR_BODY_LENGTH = 4_096;

    private final RestClient.Builder restClientBuilder;
    private final PcsRequestFactory requestFactory;

    public void submitReevaluation(
            Path yamlFile, Path processesCsvFile, UUID jobId, String baseUrl, String token, boolean allowInsecureHttp) {
        submit(PcsJobType.REEVALUATION, requestFactory.reevaluation(yamlFile, processesCsvFile), jobId, baseUrl, token,
                allowInsecureHttp);
    }

    public void submitBackfill(
            Path yamlFile, Path processDataCsvFile, UUID jobId, String baseUrl, String token, boolean allowInsecureHttp) {
        submit(PcsJobType.BACKFILL, requestFactory.backfill(yamlFile, processDataCsvFile), jobId, baseUrl, token,
                allowInsecureHttp);
    }

    public void submitRelationPublication(
            Path yamlFile, Path relationsCsvFile, UUID jobId, String baseUrl, String token, boolean allowInsecureHttp) {
        submit(PcsJobType.RELATION_PUBLICATION,
                requestFactory.relationPublication(yamlFile, relationsCsvFile), jobId, baseUrl, token,
                allowInsecureHttp);
    }

    void submit(PcsJobType type, PreparedPcsRequest request, UUID jobId, String baseUrl, String token,
                boolean allowInsecureHttp) {
        URI endpoint = endpoint(baseUrl, type, jobId, allowInsecureHttp);
        try {
            restClientBuilder.build().put()
                    .uri(endpoint)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(APPLICATION_YAML)
                    .accept(APPLICATION_YAML)
                    .body(request.yaml())
                    .exchange((httpRequest, response) -> handleSubmissionResponse(response, type, jobId, token));
        } catch (RestClientException e) {
            throw connectionException(baseUrl, e);
        }
    }

    public String report(PcsJobType type, UUID jobId, String baseUrl, Path output, String token,
                         boolean allowInsecureHttp) {
        validateOutput(output);
        URI endpoint = endpoint(baseUrl, type, jobId, allowInsecureHttp);
        String report;
        try {
            report = restClientBuilder.build().get()
                    .uri(endpoint)
                    .headers(headers -> headers.setBearerAuth(token))
                    .accept(APPLICATION_YAML)
                    .exchange((httpRequest, response) -> handleReportResponse(response, type, jobId, token));
        } catch (RestClientException e) {
            throw connectionException(baseUrl, e);
        }

        if (output == null) {
            return report;
        }
        try {
            Files.writeString(output, report, StandardCharsets.UTF_8);
            return null;
        } catch (IOException e) {
            throw new PcsException("Could not write PCS report to " + output + ": " + e.getMessage(), e);
        }
    }

    private Void handleSubmissionResponse(
            ClientHttpResponse response, PcsJobType type, UUID jobId, String token) throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return null;
        }
        throw httpException(response, type, jobId, "processcontextjob:write", token);
    }

    private String handleReportResponse(
            ClientHttpResponse response, PcsJobType type, UUID jobId, String token) throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return responseBody(response);
        }
        throw httpException(response, type, jobId, "processcontextjob:read", token);
    }

    private PcsException httpException(
            ClientHttpResponse response, PcsJobType type, UUID jobId, String requiredRole, String token) throws IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        String body = redactToken(errorResponseBody(response).trim(), token);
        String details = body.isEmpty() ? "" : ": " + body;
        if (status == HttpStatus.BAD_REQUEST) {
            return new PcsException("PCS rejected the " + type.endpointSegment() + " job request" + details);
        }
        if (status == HttpStatus.UNAUTHORIZED) {
            return new PcsException("PCS rejected the access token. Provide a valid, non-expired token.");
        }
        if (status == HttpStatus.FORBIDDEN) {
            return new PcsException("PCS denied the request. The access token needs role " + requiredRole + ".");
        }
        if (status == HttpStatus.NOT_FOUND) {
            return new PcsException("PCS " + type.endpointSegment() + " job " + jobId + " was not found" + details);
        }
        if (status == HttpStatus.CONFLICT) {
            return new PcsException("PCS job " + jobId
                    + " already exists with different content. Reuse the UUID only for an identical retry" + details);
        }
        return new PcsException("PCS request failed with HTTP status " + response.getStatusCode().value() + details);
    }

    private String responseBody(ClientHttpResponse response) throws IOException {
        return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
    }

    private String errorResponseBody(ClientHttpResponse response) throws IOException {
        byte[] bytes = response.getBody().readNBytes(MAX_ERROR_BODY_LENGTH + 1);
        boolean truncated = bytes.length > MAX_ERROR_BODY_LENGTH;
        int length = Math.min(bytes.length, MAX_ERROR_BODY_LENGTH);
        return new String(bytes, 0, length, StandardCharsets.UTF_8) + (truncated ? "..." : "");
    }

    private PcsException connectionException(String baseUrl, RestClientException cause) {
        return new PcsException("Could not connect to PCS at " + safeServerDescription(baseUrl)
                + ". Check the URL, network connection and TLS configuration: " + cause.getMessage(), cause);
    }

    private void validateOutput(Path output) {
        if (output == null) {
            return;
        }
        if (Files.isDirectory(output)) {
            throw new PcsException("Report output path is a directory: " + output);
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new PcsException("Report output directory does not exist: " + parent);
        }
        if (!Files.isWritable(parent) || Files.exists(output) && !Files.isWritable(output)) {
            throw new PcsException("Report output path is not writable: " + output);
        }
    }

    private URI endpoint(String baseUrl, PcsJobType type, UUID jobId, boolean allowInsecureHttp) {
        URI baseUri;
        try {
            baseUri = URI.create(baseUrl == null ? "" : baseUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new PcsException("Invalid PCS URL '" + baseUrl + "'. Provide an HTTP or HTTPS PCS URL.", e);
        }
        if (baseUri.getHost() == null || !("http".equalsIgnoreCase(baseUri.getScheme())
                || "https".equalsIgnoreCase(baseUri.getScheme()))) {
            throw new PcsException("Invalid PCS URL '" + baseUrl + "'. Provide an HTTPS PCS URL.");
        }
        if (baseUri.getQuery() != null || baseUri.getFragment() != null) {
            throw new PcsException("Invalid PCS URL '" + baseUrl + "'. Query parameters and fragments are not allowed.");
        }
        if (baseUri.getUserInfo() != null) {
            throw new PcsException("Invalid PCS URL. User information is not allowed in the URL.");
        }
        if ("http".equalsIgnoreCase(baseUri.getScheme())) {
            if (!allowInsecureHttp) {
                throw new PcsException("Insecure HTTP PCS URLs are disabled. Use HTTPS or explicitly set "
                        + "--allow-insecure-http=true for a local loopback PCS URL.");
            }
            if (!isLoopbackHost(baseUri.getHost())) {
                throw new PcsException("Insecure HTTP is allowed only for localhost, 127.0.0.1 or ::1.");
            }
        }
        return UriComponentsBuilder.fromUri(baseUri)
                .pathSegment("api", type.endpointSegment() + "-jobs", jobId.toString())
                .build(true)
                .toUri();
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private String safeServerDescription(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + port;
        } catch (IllegalArgumentException e) {
            return "the configured URL";
        }
    }

    private String redactToken(String value, String token) {
        return token == null || token.isEmpty() ? value : value.replace(token, "[REDACTED]");
    }
}
