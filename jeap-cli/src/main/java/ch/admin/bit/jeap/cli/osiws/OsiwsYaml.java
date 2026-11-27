package ch.admin.bit.jeap.cli.osiws;

import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML reading and writing shared by requests and reports. Writes like the index writer: numeric-looking text such as
 * the id '012345' stays quoted, so it is read back as text with its leading zeros.
 */
final class OsiwsYaml {

    /**
     * Replaces snakeyaml-engine's default of 3 MiB, which is below the index writer's default request limit
     * (max-request-bytes, 8 MiB) and the size of large report pages. The alias limits against expansion attacks stay.
     */
    static final int MAX_DOCUMENT_CODE_POINTS = 64 * 1024 * 1024;

    private static final YAMLMapper MAPPER = YAMLMapper.builder(YAMLFactory.builder()
                    .loadSettings(LoadSettings.builder().setCodePointLimit(MAX_DOCUMENT_CODE_POINTS).build())
                    .build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
            .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
            .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
            // one line per value, so that the report can be searched line by line
            .disable(YAMLWriteFeature.SPLIT_LINES)
            .build();

    private OsiwsYaml() {
    }

    /**
     * @return the root object, or null if the document is empty
     * @throws JacksonException if the document is not valid YAML
     */
    static Object read(String yaml) {
        String content = yaml.startsWith("\uFEFF") ? yaml.substring(1) : yaml;
        try (JsonParser parser = MAPPER.createParser(content)) {
            return parser.nextToken() == null ? null : MAPPER.readValue(parser, Object.class);
        }
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new OsiwsException("Could not write YAML: " + e.getOriginalMessage(), e);
        }
    }

    static Map<String, Object> map(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new OsiwsException(fieldName + " must be a YAML object.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    static List<?> list(Object value, String fieldName) {
        if (!(value instanceof List<?> list)) {
            throw new OsiwsException(fieldName + " must be a YAML list.");
        }
        return list;
    }
}
