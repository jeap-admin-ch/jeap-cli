package ch.admin.bit.jeap.cli.backfill;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class PasBackfillService {

    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");

    private final RestClient.Builder restClientBuilder;

    public String send(Path yamlFile, String jobId, String url, String accessToken) {
        String yaml = readYaml(yamlFile);
        String endpoint = jobEndpoint(url, jobId);

        return restClientBuilder.build().put()
                .uri(endpoint)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(APPLICATION_YAML)
                .body(yaml)
                .exchange((request, response) -> handleSendResponse(response, jobId));
    }

    public String report(String jobId, String url, Path output, String accessToken) {
        String endpoint = jobReportEndpoint(url, jobId);

        String report = restClientBuilder.build().get()
                .uri(endpoint)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .accept(APPLICATION_YAML)
                .exchange((request, response) -> handleReportResponse(response, jobId));

        if (output == null) {
            return report;
        }

        try {
            Files.writeString(output, report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PasBackfillException("Could not write report to " + output + ": " + e.getMessage());
        }
        return "Report written to " + output;
    }

    private String readYaml(Path yamlFile) {
        try {
            return Files.readString(yamlFile, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new PasBackfillException("File not found: " + yamlFile);
        } catch (IOException e) {
            throw new PasBackfillException("Could not read file " + yamlFile + ": " + e.getMessage());
        }
    }

    private String handleSendResponse(ClientHttpResponse response, String jobId) throws IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        if (status == HttpStatus.OK || status == HttpStatus.CREATED) {
            return "Backfill job " + jobId + " created successfully.";
        }
        if (status == HttpStatus.CONFLICT) {
            throw new PasBackfillException("Job " + jobId + " already exists.");
        }
        if (status == HttpStatus.BAD_REQUEST) {
            throw new PasBackfillException(responseBody(response));
        }
        throw new PasBackfillException("PAS request failed with HTTP status " + response.getStatusCode().value());
    }

    private String handleReportResponse(ClientHttpResponse response, String jobId) throws IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        if (status == HttpStatus.OK) {
            return responseBody(response);
        }
        if (status == HttpStatus.NOT_FOUND) {
            throw new PasBackfillException("Job " + jobId + " not found.");
        }
        throw new PasBackfillException("PAS request failed with HTTP status " + response.getStatusCode().value());
    }

    private String responseBody(ClientHttpResponse response) throws IOException {
        return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
    }

    private String jobEndpoint(String baseUrl, String jobId) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("api", "jobs", jobId)
                .toUriString();
    }

    private String jobReportEndpoint(String baseUrl, String jobId) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("api", "jobs", jobId, "report")
                .toUriString();
    }
}
