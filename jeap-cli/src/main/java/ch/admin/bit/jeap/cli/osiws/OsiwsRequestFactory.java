package ch.admin.bit.jeap.cli.osiws;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the canonical YAML request of an upsert or delete job from a YAML file and optionally a CSV file with the
 * origin references. Everything the index writer would reject for certain is rejected before contacting it.
 */
@Component
@RequiredArgsConstructor
class OsiwsRequestFactory {

    private static final String ORIGIN_REFERENCES = "originReferences";
    private static final String INDEX_MAJOR_VERSION = "indexMajorVersion";
    private static final String UPSERT_OPERATION = "UPSERT";
    private static final Set<String> ROLLBACK_FIELDS = Set.of("allowRollback", "allowVersionRollback");

    private final OsiwsReferenceCsvParser csvParser;

    PreparedOsiwsRequest upsert(Path yamlFile, Path csvFile) {
        Map<String, Object> yaml = readYaml(yamlFile);
        requireOnlyKeys(yaml, List.of("message", "operation", ORIGIN_REFERENCES), "", yamlFile);

        Map<String, Object> message = OsiwsYaml.map(yaml.get("message"), "message");
        requireOnlyKeys(message, List.of("messageName", "topicName"), "message.", yamlFile);
        Map<String, Object> operation = OsiwsYaml.map(yaml.get("operation"), "operation");
        requireOnlyKeys(operation, List.of("indexType", "indexOperation", INDEX_MAJOR_VERSION), "operation.", yamlFile);
        Object indexOperation = operation.get("indexOperation");
        if (indexOperation != null && !UPSERT_OPERATION.equals(indexOperation)) {
            throw new OsiwsException("operation.indexOperation must be UPSERT for an upsert job (or be omitted).");
        }

        Map<String, Object> canonicalMessage = new LinkedHashMap<>();
        canonicalMessage.put("messageName", OsiwsValidation.requiredText(message.get("messageName"), "message.messageName"));
        canonicalMessage.put("topicName", OsiwsValidation.requiredText(message.get("topicName"), "message.topicName"));
        Map<String, Object> canonicalOperation = new LinkedHashMap<>();
        canonicalOperation.put("indexType", OsiwsValidation.requiredText(operation.get("indexType"), "operation.indexType"));
        canonicalOperation.put("indexOperation", UPSERT_OPERATION);
        putIfNotNull(canonicalOperation, INDEX_MAJOR_VERSION, OsiwsValidation.indexMajorVersion(
                operation.get(INDEX_MAJOR_VERSION), "operation." + INDEX_MAJOR_VERSION));
        List<OriginReference> references = references(yaml, yamlFile, csvFile, OsiwsJobType.UPSERT);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("message", canonicalMessage);
        request.put("operation", canonicalOperation);
        request.put(ORIGIN_REFERENCES, referencesYaml(references));
        return new PreparedOsiwsRequest(OsiwsYaml.write(request), references.size());
    }

    PreparedOsiwsRequest delete(Path yamlFile, Path csvFile) {
        Map<String, Object> yaml = readYaml(yamlFile);
        requireOnlyKeys(yaml, List.of("indexType", INDEX_MAJOR_VERSION, ORIGIN_REFERENCES), "", yamlFile);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("indexType", OsiwsValidation.requiredText(yaml.get("indexType"), "indexType"));
        putIfNotNull(request, INDEX_MAJOR_VERSION,
                OsiwsValidation.indexMajorVersion(yaml.get(INDEX_MAJOR_VERSION), INDEX_MAJOR_VERSION));
        List<OriginReference> references = references(yaml, yamlFile, csvFile, OsiwsJobType.DELETE);
        request.put(ORIGIN_REFERENCES, referencesYaml(references));
        return new PreparedOsiwsRequest(OsiwsYaml.write(request), references.size());
    }

    private List<OriginReference> references(Map<String, Object> yaml, Path yamlFile, Path csvFile, OsiwsJobType type) {
        List<OriginReference> references;
        if (csvFile != null) {
            if (yaml.containsKey(ORIGIN_REFERENCES)) {
                throw new OsiwsException("originReferences are defined in both " + yamlFile
                        + " and --csv. Use one source only.");
            }
            references = csvParser.parse(csvFile, type);
        } else {
            references = yaml.containsKey(ORIGIN_REFERENCES)
                    ? yamlReferences(yaml.get(ORIGIN_REFERENCES), yamlFile, type)
                    : List.of();
        }
        if (references.isEmpty()) {
            throw new OsiwsException("No originReferences provided. Define them in " + yamlFile + " or use --csv.");
        }
        return references;
    }

    private List<OriginReference> yamlReferences(Object value, Path yamlFile, OsiwsJobType type) {
        List<?> values = OsiwsYaml.list(value, ORIGIN_REFERENCES);
        List<OriginReference> references = new ArrayList<>(values.size());
        Map<String, String> firstLocationById = new HashMap<>();
        for (int index = 0; index < values.size(); index++) {
            String location = ORIGIN_REFERENCES + "[" + index + "] of " + yamlFile;
            Map<String, Object> reference = OsiwsYaml.map(values.get(index), ORIGIN_REFERENCES + "[" + index + "]");
            if (type == OsiwsJobType.DELETE && reference.containsKey("version")) {
                throw new OsiwsException("Delete jobs have ids only. Remove version from " + location + ".");
            }
            requireOnlyKeys(reference, type == OsiwsJobType.DELETE ? List.of("id") : List.of("id", "version"),
                    ORIGIN_REFERENCES + "[" + index + "].", yamlFile);
            String id = OsiwsValidation.referenceId(reference.get("id"), location);
            String version = OsiwsValidation.referenceVersion(reference.get("version"), location);
            OsiwsValidation.requireUnique(firstLocationById, id, location);
            references.add(new OriginReference(id, version));
        }
        return references;
    }

    private static List<Map<String, Object>> referencesYaml(List<OriginReference> references) {
        List<Map<String, Object>> values = new ArrayList<>(references.size());
        for (OriginReference reference : references) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", reference.id());
            putIfNotNull(value, "version", reference.version());
            values.add(value);
        }
        return values;
    }

    private Map<String, Object> readYaml(Path yamlFile) {
        String content;
        try {
            content = Files.readString(yamlFile, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new OsiwsException("File not found: " + yamlFile, e);
        } catch (CharacterCodingException e) {
            throw new OsiwsException("YAML file " + yamlFile + " is not valid UTF-8.", e);
        } catch (IOException e) {
            throw new OsiwsException("Could not read YAML file " + yamlFile + ": " + e.getMessage(), e);
        }
        Object root;
        try {
            root = OsiwsYaml.read(content);
        } catch (JacksonException e) {
            throw new OsiwsException("Could not parse YAML file " + yamlFile + ": " + e.getOriginalMessage(), e);
        }
        if (!(root instanceof Map<?, ?>)) {
            throw new OsiwsException("YAML file " + yamlFile + " must contain a YAML object with the job fields.");
        }
        return OsiwsYaml.map(root, "YAML root");
    }

    private static void requireOnlyKeys(Map<String, Object> values, List<String> allowed, String prefix, Path source) {
        Set<String> unknown = new LinkedHashSet<>(values.keySet());
        allowed.forEach(unknown::remove);
        for (String field : unknown) {
            if (ROLLBACK_FIELDS.contains(field)) {
                throw new OsiwsException(prefix + field + " in " + source + " is not supported: maintenance jobs "
                        + "never compare versions, so there is no rollback control. Remove the field.");
            }
        }
        if (!unknown.isEmpty()) {
            throw new OsiwsException("Unknown YAML field" + (unknown.size() == 1 ? " " : "s ")
                    + String.join(", ", unknown.stream().map(field -> prefix + field).toList()) + " in " + source
                    + ". Allowed: " + String.join(", ", allowed.stream().map(field -> prefix + field).toList()) + ".");
        }
    }

    private static void putIfNotNull(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}
