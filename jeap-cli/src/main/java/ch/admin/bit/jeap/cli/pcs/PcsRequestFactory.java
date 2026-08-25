package ch.admin.bit.jeap.cli.pcs;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PcsRequestFactory {

    private static final Comparator<ProcessDataValue> PROCESS_DATA_COMPARATOR = Comparator
            .comparing(ProcessDataValue::key)
            .thenComparing(ProcessDataValue::value)
            .thenComparing(ProcessDataValue::role, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final PcsProperties properties;
    private final ReevaluationCsvParser reevaluationCsvParser;
    private final ProcessDataCsvParser processDataCsvParser;
    private final RelationCsvParser relationCsvParser;
    private final YAMLMapper yamlMapper = YAMLMapper.builder()
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
            .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
            .build();

    PreparedPcsRequest reevaluation(Path yamlFile, Path processesCsvFile) {
        Map<String, Object> yaml = readYaml(yamlFile);
        requireOnlyKeys(yaml, Set.of("process-template-name", "processes"), yamlFile);
        String templateName = PcsValidation.requiredString(
                yaml.get("process-template-name"), "process-template-name", properties);

        List<ReevaluationProcess> processes;
        if (processesCsvFile != null) {
            if (yaml.containsKey("processes")) {
                throw new PcsException("processes are defined in both YAML and --processes-csv. Use one source only.");
            }
            processes = reevaluationCsvParser.parse(processesCsvFile);
        } else {
            processes = yamlProcesses(yaml.get("processes"));
        }
        requireNotEmpty(processes, "No processes provided. Define them in YAML or use --processes-csv.");
        PcsValidation.validateTaskCount(processes.size(), properties);

        ReevaluationRequest request = new ReevaluationRequest(templateName,
                processes.stream().sorted(Comparator.comparing(ReevaluationProcess::originProcessId)).toList());
        return prepared(reevaluationYaml(request), request.processes().size());
    }

    PreparedPcsRequest backfill(Path yamlFile, Path processDataCsvFile) {
        Map<String, Object> yaml = readYaml(yamlFile);
        requireOnlyKeys(yaml, Set.of("process-template-name", "entries"), yamlFile);
        String templateName = PcsValidation.requiredString(
                yaml.get("process-template-name"), "process-template-name", properties);

        List<ProcessDataEntry> entries;
        if (processDataCsvFile != null) {
            if (yaml.containsKey("entries")) {
                throw new PcsException("entries are defined in both YAML and --process-data-csv. Use one source only.");
            }
            entries = groupCsvProcessData(processDataCsvParser.parse(processDataCsvFile));
        } else {
            entries = yamlEntries(yaml.get("entries"));
        }
        requireNotEmpty(entries, "No entries provided. Define them in YAML or use --process-data-csv.");
        PcsValidation.validateTaskCount(entries.size(), properties);
        entries.forEach(entry -> validateProcessDataPerTask(entry.processData().size(), entry.originProcessId()));
        validateProcessDataCount(entries.stream().mapToInt(entry -> entry.processData().size()).sum());

        BackfillRequest request = new BackfillRequest(templateName, entries.stream()
                .sorted(Comparator.comparing(ProcessDataEntry::originProcessId))
                .map(entry -> new ProcessDataEntry(entry.originProcessId(),
                        entry.processData().stream().sorted(PROCESS_DATA_COMPARATOR).toList()))
                .toList());
        return prepared(backfillYaml(request), request.entries().size());
    }

    PreparedPcsRequest relationPublication(Path yamlFile, Path relationsCsvFile) {
        if (yamlFile != null && relationsCsvFile != null) {
            throw new PcsException("relationIds are defined by both --file and --relations-csv. Use one source only.");
        }
        if (yamlFile == null && relationsCsvFile == null) {
            throw new PcsException("No relationIds provided. Use --file or --relations-csv.");
        }

        List<UUID> relationIds;
        if (relationsCsvFile != null) {
            relationIds = relationCsvParser.parse(relationsCsvFile);
        } else {
            Map<String, Object> yaml = readYaml(yamlFile);
            requireOnlyKeys(yaml, Set.of("relationIds"), yamlFile);
            relationIds = yamlRelationIds(yaml.get("relationIds"));
        }
        requireNotEmpty(relationIds, "No relationIds provided. Use --file or --relations-csv.");
        PcsValidation.validateTaskCount(relationIds.size(), properties);

        RelationPublicationRequest request = new RelationPublicationRequest(relationIds.stream()
                .sorted()
                .toList());
        return prepared(relationPublicationYaml(request), request.relationIds().size());
    }

    private List<ReevaluationProcess> yamlProcesses(Object value) {
        List<?> values = requiredList(value, "processes");
        List<ReevaluationProcess> processes = new ArrayList<>();
        Set<String> processIds = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> process = requiredMap(values.get(index), "processes[" + index + "]");
            requireOnlyKeys(process, Set.of("origin-process-id"), null);
            String processId = PcsValidation.requiredString(
                    process.get("origin-process-id"), "origin-process-id", properties);
            if (!processIds.add(processId)) {
                throw new PcsException("processes contains duplicate originProcessId '" + processId + "'.");
            }
            processes.add(new ReevaluationProcess(processId));
        }
        return processes;
    }

    private List<ProcessDataEntry> yamlEntries(Object value) {
        List<?> values = requiredList(value, "entries");
        List<ProcessDataEntry> entries = new ArrayList<>();
        Set<String> processIds = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> entry = requiredMap(values.get(index), "entries[" + index + "]");
            requireOnlyKeys(entry, Set.of("origin-process-id", "process-data"), null);
            String processId = PcsValidation.requiredString(
                    entry.get("origin-process-id"), "origin-process-id", properties);
            if (!processIds.add(processId)) {
                throw new PcsException("entries contains duplicate originProcessId '" + processId + "'.");
            }
            List<?> processDataValues = requiredList(entry.get("process-data"), "process-data");
            if (processDataValues.isEmpty()) {
                throw new PcsException("processData must not be empty for originProcessId '" + processId + "'.");
            }
            validateProcessDataPerTask(processDataValues.size(), processId);
            List<ProcessDataValue> processData = new ArrayList<>();
            Set<ProcessDataValue> uniqueValues = new HashSet<>();
            for (int dataIndex = 0; dataIndex < processDataValues.size(); dataIndex++) {
                Map<String, Object> data = requiredMap(
                        processDataValues.get(dataIndex), "processData[" + dataIndex + "]");
                requireOnlyKeys(data, Set.of("key", "value", "role"), null);
                ProcessDataValue processDataValue = new ProcessDataValue(
                        PcsValidation.requiredString(data.get("key"), "key", properties),
                        PcsValidation.requiredString(data.get("value"), "value", properties),
                        PcsValidation.optionalString(data.get("role"), "role", properties));
                if (!uniqueValues.add(processDataValue)) {
                    throw new PcsException("processData contains a duplicate value for originProcessId '"
                            + processId + "'.");
                }
                processData.add(processDataValue);
            }
            entries.add(new ProcessDataEntry(processId, processData));
        }
        return entries;
    }

    private List<UUID> yamlRelationIds(Object value) {
        List<?> values = requiredList(value, "relationIds");
        List<UUID> relationIds = new ArrayList<>();
        Set<UUID> uniqueIds = new HashSet<>();
        for (Object relationIdValue : values) {
            UUID relationId = PcsValidation.uuid(relationIdValue, "relationId", properties);
            if (!uniqueIds.add(relationId)) {
                throw new PcsException("relationIds contains duplicate UUID '" + relationId + "'.");
            }
            relationIds.add(relationId);
        }
        return relationIds;
    }

    private List<ProcessDataEntry> groupCsvProcessData(List<ProcessDataCsvRow> rows) {
        Map<String, List<ProcessDataValue>> valuesByProcess = new TreeMap<>();
        for (ProcessDataCsvRow row : rows) {
            valuesByProcess.computeIfAbsent(row.originProcessId(), ignored -> new ArrayList<>())
                    .add(row.processData());
        }
        return valuesByProcess.entrySet().stream()
                .map(entry -> new ProcessDataEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<String, Object> readYaml(Path yamlFile) {
        if (yamlFile == null) {
            throw new PcsException("Missing YAML file.");
        }
        try {
            if (Files.size(yamlFile) > properties.getLimits().getMaxRequestBytes()) {
                throw new PcsException("YAML file " + yamlFile + " exceeds the configured maximum request size of "
                        + properties.getLimits().getMaxRequestBytes() + " bytes.");
            }
            String yaml = Files.readString(yamlFile, StandardCharsets.UTF_8);
            if (yaml.startsWith("\uFEFF")) {
                yaml = yaml.substring(1);
            }
            Map<?, ?> values = yamlMapper.readValue(yaml, Map.class);
            return requiredMap(values, "YAML root");
        } catch (NoSuchFileException e) {
            throw new PcsException("File not found: " + yamlFile, e);
        } catch (PcsException e) {
            throw e;
        } catch (JacksonException e) {
            throw new PcsException("Could not parse YAML file " + yamlFile + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new PcsException("Could not read YAML file " + yamlFile + ": " + e.getMessage(), e);
        }
    }

    private Map<String, Object> requiredMap(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new PcsException(fieldName + " must be a YAML object.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<?> requiredList(Object value, String fieldName) {
        if (!(value instanceof List<?> list)) {
            throw new PcsException(fieldName + " must be a YAML list.");
        }
        return list;
    }

    private void requireOnlyKeys(Map<String, Object> values, Set<String> expected, Path source) {
        Set<String> unknown = new HashSet<>(values.keySet());
        unknown.removeAll(expected);
        if (!unknown.isEmpty()) {
            String location = source == null ? "" : " in " + source;
            throw new PcsException("Unknown YAML field" + (unknown.size() == 1 ? "" : "s") + location + ": "
                    + String.join(", ", unknown.stream().sorted().toList()));
        }
    }

    private void requireNotEmpty(List<?> values, String message) {
        if (values.isEmpty()) {
            throw new PcsException(message);
        }
    }

    private void validateProcessDataCount(int count) {
        int maximum = properties.getLimits().getMaxProcessDataValuesPerJob();
        if (count > maximum) {
            throw new PcsException("Backfill request exceeds the PCS per-job maximum of " + maximum
                    + " process-data values.");
        }
    }

    private void validateProcessDataPerTask(int count, String processId) {
        int maximum = properties.getLimits().getMaxProcessDataValuesPerTask();
        if (count > maximum) {
            throw new PcsException("process-data exceeds the PCS maximum of " + maximum
                    + " values for origin-process-id '" + processId + "'.");
        }
    }

    private PreparedPcsRequest prepared(Map<String, Object> values, int taskCount) {
        try {
            String yaml = yamlMapper.writeValueAsString(values);
            int bytes = yaml.getBytes(StandardCharsets.UTF_8).length;
            int maximum = properties.getLimits().getMaxRequestBytes();
            if (bytes > maximum) {
                throw new PcsException("Canonical YAML request exceeds the configured maximum request size of "
                        + maximum + " bytes.");
            }
            return new PreparedPcsRequest(yaml, taskCount);
        } catch (JacksonException e) {
            throw new PcsException("Could not serialize canonical PCS request: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> reevaluationYaml(ReevaluationRequest request) {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("process-template-name", request.processTemplateName());
        yaml.put("processes", request.processes().stream().map(process -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("origin-process-id", process.originProcessId());
            return value;
        }).toList());
        return yaml;
    }

    private Map<String, Object> backfillYaml(BackfillRequest request) {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("process-template-name", request.processTemplateName());
        yaml.put("entries", request.entries().stream().map(entry -> {
            Map<String, Object> entryValue = new LinkedHashMap<>();
            entryValue.put("origin-process-id", entry.originProcessId());
            entryValue.put("process-data", entry.processData().stream().map(data -> {
                Map<String, Object> dataValue = new LinkedHashMap<>();
                dataValue.put("key", data.key());
                dataValue.put("value", data.value());
                if (data.role() != null) {
                    dataValue.put("role", data.role());
                }
                return dataValue;
            }).toList());
            return entryValue;
        }).toList());
        return yaml;
    }

    private Map<String, Object> relationPublicationYaml(RelationPublicationRequest request) {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("relationIds", request.relationIds().stream().map(UUID::toString).toList());
        return yaml;
    }
}
