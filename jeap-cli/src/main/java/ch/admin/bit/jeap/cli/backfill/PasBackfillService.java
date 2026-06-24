package ch.admin.bit.jeap.cli.backfill;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PasBackfillService {

    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");
    private static final String ARCHIVE_DATA_REFERENCES = "archiveDataReferences";

    private final RestClient.Builder restClientBuilder;
    private final BackfillReferenceCsvParser referenceCsvParser;
    private final YAMLMapper yamlMapper = YAMLMapper.builder().build();

    public String send(Path yamlFile, String jobId, String url, String accessToken) {
        return send(yamlFile, null, jobId, url, accessToken);
    }

    public String send(Path yamlFile, Path referencesCsvFile, String jobId, String url, String accessToken) {
        String yaml = readYaml(yamlFile);
        BackfillJobRequestDto backfillJobRequest = createBackfillJobRequest(yamlFile, yaml, referencesCsvFile);
        String requestYaml = writeYaml(yamlFile, backfillJobRequest);
        String endpoint = jobEndpoint(url, jobId);
        int numberOfReferences = backfillJobRequest.archiveDataReferences().size();

        return restClientBuilder.build().put()
                .uri(endpoint)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(APPLICATION_YAML)
                .body(requestYaml)
                .exchange((request, response) -> handleSendResponse(response, jobId, numberOfReferences));
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

    private BackfillJobRequestDto createBackfillJobRequest(Path yamlFile, String yaml, Path referencesCsvFile) {
        Map<String, Object> yamlValues = readYamlValues(yamlFile, yaml);
        boolean yamlReferencesDefined = yamlValues.containsKey(ARCHIVE_DATA_REFERENCES)
                && yamlValues.get(ARCHIVE_DATA_REFERENCES) != null;
        List<ArchiveDataReferenceDto> yamlReferences = yamlReferencesDefined
                ? toArchiveDataReferences(yamlValues.get(ARCHIVE_DATA_REFERENCES))
                : List.of();

        if (referencesCsvFile != null && yamlReferencesDefined) {
            throw new PasBackfillException("Error: archiveDataReferences defined in both YAML and --references-csv. Use one source only.");
        }

        List<ArchiveDataReferenceDto> archiveDataReferences;
        if (referencesCsvFile != null) {
            archiveDataReferences = readCsvReferences(referencesCsvFile);
        } else {
            archiveDataReferences = yamlReferences;
        }

        if (archiveDataReferences.isEmpty()) {
            throw new PasBackfillException("Error: No archiveDataReferences provided. Define them in the YAML file or use --references-csv.");
        }

        return new BackfillJobRequestDto(
                stringValue(yamlValues.get("message")),
                stringValue(yamlValues.get("topic")),
                integerValue(yamlValues.get("num-of-retry"), "num-of-retry"),
                archiveDataReferences);
    }

    private Map<String, Object> readYamlValues(Path yamlFile, String yaml) {
        try {
            Map<?, ?> values = yamlMapper.readValue(yaml, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            if (values != null) {
                values.forEach((key, value) -> result.put(String.valueOf(key), value));
            }
            return result;
        } catch (JacksonException e) {
            throw new PasBackfillException("Could not parse file " + yamlFile + ": " + e.getMessage());
        }
    }

    private List<ArchiveDataReferenceDto> readCsvReferences(Path referencesCsvFile) {
        try {
            return referenceCsvParser.parse(referencesCsvFile);
        } catch (NoSuchFileException e) {
            throw new PasBackfillException("File not found: " + referencesCsvFile);
        } catch (IllegalArgumentException e) {
            throw new PasBackfillException(e.getMessage());
        } catch (IOException e) {
            throw new PasBackfillException("Could not read file " + referencesCsvFile + ": " + e.getMessage());
        }
    }

    private List<ArchiveDataReferenceDto> toArchiveDataReferences(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }

        List<ArchiveDataReferenceDto> references = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> reference) {
                references.add(new ArchiveDataReferenceDto(
                        stringValue(reference.get("id")),
                        integerValue(reference.get("version"), "archiveDataReferences[].version")));
            }
        }
        return references;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new PasBackfillException("Invalid YAML value '" + value + "' for " + fieldName + ". Must be an integer.");
        }
    }

    private String writeYaml(Path yamlFile, BackfillJobRequestDto request) {
        Map<String, Object> yamlValues = new LinkedHashMap<>();
        yamlValues.put("message", request.message());
        yamlValues.put("topic", request.topic());
        if (request.numOfRetry() != null) {
            yamlValues.put("num-of-retry", request.numOfRetry());
        }
        yamlValues.put(ARCHIVE_DATA_REFERENCES, toYamlReferenceValues(request.archiveDataReferences()));

        try {
            return yamlMapper.writeValueAsString(yamlValues);
        } catch (JacksonException e) {
            throw new PasBackfillException("Could not write merged backfill job from " + yamlFile + ": " + e.getMessage());
        }
    }

    private List<Map<String, Object>> toYamlReferenceValues(List<ArchiveDataReferenceDto> references) {
        List<Map<String, Object>> yamlReferenceValues = new ArrayList<>();
        for (ArchiveDataReferenceDto reference : references) {
            Map<String, Object> yamlReferenceValue = new LinkedHashMap<>();
            yamlReferenceValue.put("id", reference.id());
            if (reference.version() != null) {
                yamlReferenceValue.put("version", reference.version());
            }
            yamlReferenceValues.add(yamlReferenceValue);
        }
        return yamlReferenceValues;
    }

    private String handleSendResponse(ClientHttpResponse response, String jobId, int numberOfReferences) throws IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        if (status == HttpStatus.OK || status == HttpStatus.CREATED) {
            return "Backfill job " + jobId + " created successfully. " + numberOfReferences + " references submitted.";
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
