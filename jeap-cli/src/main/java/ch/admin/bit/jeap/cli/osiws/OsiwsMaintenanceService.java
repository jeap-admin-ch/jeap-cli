package ch.admin.bit.jeap.cli.osiws;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Submits maintenance jobs to the OpenSearch index writer service and exports their reports.
 */
@Service
@RequiredArgsConstructor
public class OsiwsMaintenanceService {

    public static final int MAX_PAGE_SIZE = 10_000;
    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");
    private static final MediaType APPLICATION_YAML_UTF8 = new MediaType("application", "yaml", StandardCharsets.UTF_8);
    private static final int MAX_ERROR_BODY_LENGTH = 4_096;

    private final RestClient.Builder restClientBuilder;
    private final OsiwsRequestFactory requestFactory;
    private final OsiwsConsole console;

    /**
     * Validates the input, then submits it. The job id is written to stderr before the request is sent, so that it
     * is known even if the outcome of the request is not.
     */
    public void submit(OsiwsJobType type, Path yamlFile, Path csvFile, UUID jobId, String baseUrl, String token,
                       boolean allowInsecureHttp) {
        PreparedOsiwsRequest request = type == OsiwsJobType.UPSERT
                ? requestFactory.upsert(yamlFile, csvFile)
                : requestFactory.delete(yamlFile, csvFile);
        submit(type, request, jobId, baseUrl, token, allowInsecureHttp);
    }

    void submit(OsiwsJobType type, PreparedOsiwsRequest request, UUID jobId, String baseUrl, String token,
                boolean allowInsecureHttp) {
        URI endpoint = endpoint(baseUrl, type, jobId, allowInsecureHttp);
        console.info("Submitting " + type.cliValue() + " job " + jobId + " with " + references(request.referenceCount())
                + " to " + safeServerDescription(baseUrl) + ".");
        String report;
        try {
            report = restClientBuilder.build().put()
                    .uri(endpoint)
                    .headers(headers -> headers.setBearerAuth(token))
                    // Sent as bytes: a String body would be encoded as ISO-8859-1 for application/yaml, but the index
                    // writer reads UTF-8
                    .contentType(APPLICATION_YAML_UTF8)
                    .accept(APPLICATION_YAML)
                    .body(request.yaml().getBytes(StandardCharsets.UTF_8))
                    .exchange((httpRequest, response) -> handleSubmissionResponse(response, type, jobId, baseUrl, token));
        } catch (RestClientException e) {
            throw new OsiwsException("Could not complete the request to the index writer at "
                    + safeServerDescription(baseUrl) + ": " + e.getMessage() + ". The " + type.cliValue() + " job "
                    + jobId + " may or may not have been stored. Check it with 'jeap osiws report --job-type="
                    + type.cliValue() + " --job-id=" + jobId + "' or submit the same input again with --job-id="
                    + jobId + "; an identical retry is safe.", e);
        }
        console.info(acceptedMessage(type, jobId, request.referenceCount(), report));
    }

    /**
     * Reads every page of the report and returns them as one YAML document, or writes it to {@code output} and
     * returns null. Nothing is returned or written unless all pages were read.
     */
    public String report(OsiwsJobType type, UUID jobId, String baseUrl, Integer pageSize, Path output, String token,
                         boolean allowInsecureHttp) {
        if (pageSize != null && (pageSize < 1 || pageSize > MAX_PAGE_SIZE)) {
            throw new OsiwsException("Invalid page size " + pageSize + ". Must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        validateOutput(output);
        URI endpoint = endpoint(baseUrl, type, jobId, allowInsecureHttp);

        OsiwsReportMerger merger = new OsiwsReportMerger(type, jobId);
        String cursor = null;
        int pageNumber = 0;
        do {
            pageNumber++;
            int referencesRead = merger.referenceCount();
            try {
                cursor = merger.add(readPage(pageUri(endpoint, pageSize, cursor), type, jobId, baseUrl, token));
            } catch (OsiwsException e) {
                if (pageNumber == 1) {
                    throw e;
                }
                throw new OsiwsException("Report export incomplete, nothing was written: page " + pageNumber
                        + " failed after " + referencesRead + " of " + merger.totalReferences()
                        + " references were read. " + e.getMessage(), e);
            }
        } while (cursor != null);
        String report;
        try {
            report = merger.mergedYaml();
        } catch (OsiwsException e) {
            throw new OsiwsException("Report export incomplete, nothing was written. " + e.getMessage(), e);
        }
        if (pageNumber > 1) {
            console.info("Read " + references(merger.referenceCount()) + " of " + type.cliValue() + " job " + jobId
                    + " in " + pageNumber + " pages.");
        }

        if (output == null) {
            return report;
        }
        try {
            Files.writeString(output, report, StandardCharsets.UTF_8);
            return null;
        } catch (IOException e) {
            throw new OsiwsException("Could not write the report to " + output + ": " + e.getMessage(), e);
        }
    }

    private String readPage(URI uri, OsiwsJobType type, UUID jobId, String baseUrl, String token) {
        try {
            return restClientBuilder.build().get()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(token))
                    .accept(APPLICATION_YAML)
                    .exchange((httpRequest, response) -> handleReportResponse(response, type, jobId, baseUrl, token));
        } catch (RestClientException e) {
            throw new OsiwsException("Could not connect to the index writer at " + safeServerDescription(baseUrl)
                    + ". Check the URL, network connection and TLS configuration: " + e.getMessage(), e);
        }
    }

    private static URI pageUri(URI endpoint, Integer pageSize, String cursor) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(endpoint);
        if (pageSize != null) {
            builder.queryParam("limit", pageSize);
        }
        if (cursor != null) {
            builder.queryParam("cursor", URLEncoder.encode(cursor, StandardCharsets.UTF_8));
        }
        return builder.build(true).toUri();
    }

    private String handleSubmissionResponse(ClientHttpResponse response, OsiwsJobType type, UUID jobId,
                                            String baseUrl, String token) throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        }
        throw httpException(response, type, jobId, type.submitRole(), true, baseUrl, token);
    }

    private String handleReportResponse(ClientHttpResponse response, OsiwsJobType type, UUID jobId,
                                        String baseUrl, String token) throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        }
        throw httpException(response, type, jobId, OsiwsJobType.REPORT_ROLE, false, baseUrl, token);
    }

    private OsiwsException httpException(ClientHttpResponse response, OsiwsJobType type, UUID jobId,
                                         String requiredRole, boolean submission, String baseUrl, String token)
            throws IOException {
        int status = response.getStatusCode().value();
        ServerError error = ServerError.parse(redactToken(errorResponseBody(response).trim(), token));
        String job = type.cliValue() + " job " + jobId;
        String retry = submission ? " Retry later with --job-id=" + jobId + "; an identical retry is safe." : "";
        String message = switch (status) {
            case 400 -> "The index writer rejected the " + (submission ? job + " request" : "report request for " + job)
                    + error.details() + ".";
            case 401 -> "The index writer rejected the access token. Provide a valid, non-expired token.";
            case 403 -> "The index writer denied the request. The access token needs role " + requiredRole + ".";
            case 404 -> "JOB_NOT_FOUND".equals(error.code())
                    ? "The " + job + " was not found. Check the job ID and --job-type."
                    : "The index writer at " + safeServerDescription(baseUrl) + " has no maintenance job API. Check "
                    + "the URL and that maintenance jobs are enabled (jeap.opensearch.indexwriter.maintenance.enabled).";
            case 409 -> "The " + job + " already exists with different content" + error.details() + ". Reuse a job ID "
                    + "only to retry identical input; omit --job-id for a new job.";
            case 422 -> "The index writer refused the target of the " + job + error.details() + ". Nothing was stored.";
            case 503 -> "The index writer is temporarily unavailable" + error.details() + "." + retry;
            default -> "The index writer request failed with HTTP status " + status + error.details() + "."
                    + (status >= 500 ? retry : "");
        };
        return new OsiwsException(message);
    }

    private static String acceptedMessage(OsiwsJobType type, UUID jobId, int referenceCount, String report) {
        String message = type.displayName() + " job " + jobId + " accepted with " + references(referenceCount);
        try {
            Map<String, Object> values = OsiwsYaml.map(OsiwsYaml.read(report), "report");
            String targets = type == OsiwsJobType.UPSERT ? upsertTarget(values) : deleteTargets(values);
            Object state = values.get("job-state");
            return message + targets + (state == null ? "" : ", job-state " + state) + ".";
        } catch (OsiwsException | JacksonException e) {
            return message + ".";
        }
    }

    private static String upsertTarget(Map<String, Object> report) {
        if (!(report.get("target") instanceof Map<?, ?> target)) {
            return "";
        }
        return ": schema major " + target.get("index-major-version") + " (" + target.get("selection") + ") in "
                + target.get("physical-index");
    }

    private static String deleteTargets(Map<String, Object> report) {
        if (!(report.get("targets") instanceof List<?> targets) || targets.isEmpty()) {
            return "";
        }
        List<String> majors = targets.stream()
                .map(target -> target instanceof Map<?, ?> values ? values.get("index-major-version") : null)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        return ": schema major" + (majors.size() == 1 ? " " : "s ") + String.join(", ", majors)
                + " (" + report.get("target-selection") + ")";
    }

    private static String references(int count) {
        return count + (count == 1 ? " reference" : " references");
    }

    private static String errorResponseBody(ClientHttpResponse response) throws IOException {
        byte[] bytes = response.getBody().readNBytes(MAX_ERROR_BODY_LENGTH + 1);
        boolean truncated = bytes.length > MAX_ERROR_BODY_LENGTH;
        int length = Math.min(bytes.length, MAX_ERROR_BODY_LENGTH);
        return new String(bytes, 0, length, StandardCharsets.UTF_8) + (truncated ? "..." : "");
    }

    private static void validateOutput(Path output) {
        if (output == null) {
            return;
        }
        if (Files.isDirectory(output)) {
            throw new OsiwsException("Report output path is a directory: " + output);
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new OsiwsException("Report output directory does not exist: " + parent);
        }
        if (!Files.isWritable(parent) || Files.exists(output) && !Files.isWritable(output)) {
            throw new OsiwsException("Report output path is not writable: " + output);
        }
    }

    /**
     * Validates the base URL without ever echoing it: it may contain credentials (user info) or secrets in the query.
     */
    private static URI endpoint(String baseUrl, OsiwsJobType type, UUID jobId, boolean allowInsecureHttp) {
        URI baseUri;
        try {
            baseUri = new URI(baseUrl == null ? "" : baseUrl.trim());
        } catch (URISyntaxException e) {
            // without the cause, its message repeats the URL
            throw new OsiwsException("Invalid index writer URL. Provide an HTTPS URL such as "
                    + "https://index-writer.example.com.");
        }
        // The raw authority also covers user info that URI does not parse, e.g. with a host name it rejects
        if (baseUri.getRawAuthority() != null && baseUri.getRawAuthority().contains("@")) {
            throw new OsiwsException("Invalid index writer URL. User information is not allowed in the URL.");
        }
        if (baseUri.getHost() == null || !("http".equalsIgnoreCase(baseUri.getScheme())
                || "https".equalsIgnoreCase(baseUri.getScheme()))) {
            throw new OsiwsException("Invalid index writer URL. Provide an HTTPS URL such as "
                    + "https://index-writer.example.com.");
        }
        if (baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
            throw new OsiwsException("Invalid index writer URL. Query parameters and fragments are not allowed.");
        }
        if ("http".equalsIgnoreCase(baseUri.getScheme())) {
            if (!allowInsecureHttp) {
                throw new OsiwsException("Insecure HTTP index writer URLs are disabled. Use HTTPS or explicitly set "
                        + "--allow-insecure-http=true for a local loopback URL.");
            }
            if (!isLoopbackHost(baseUri.getHost())) {
                throw new OsiwsException("Insecure HTTP is allowed only for localhost, 127.0.0.1 or ::1.");
            }
        }
        return UriComponentsBuilder.fromUri(baseUri)
                .pathSegment("api", type.endpointSegment(), jobId.toString())
                .build(true)
                .toUri();
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private static String safeServerDescription(String baseUrl) {
        if (baseUrl == null) {
            return "the configured URL";
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + port;
        } catch (IllegalArgumentException e) {
            return "the configured URL";
        }
    }

    private static String redactToken(String value, String token) {
        return token == null || token.isEmpty() ? value : value.replace(token, "[REDACTED]");
    }

    /**
     * The YAML error body of the job API ({@code code}, {@code message}); other bodies are kept as text.
     */
    private record ServerError(String code, String message, String body) {

        static ServerError parse(String body) {
            try {
                if (OsiwsYaml.read(body) instanceof Map<?, ?> values
                        && values.get("code") instanceof String code && values.get("message") instanceof String message) {
                    return new ServerError(code, message, body);
                }
            } catch (JacksonException e) {
                // not the YAML error format of the job API
            }
            return new ServerError(null, null, body);
        }

        String details() {
            if (code != null) {
                return " (" + code + "): " + message;
            }
            return body.isEmpty() ? "" : ": " + body;
        }
    }
}
