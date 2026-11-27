package ch.admin.bit.jeap.cli.osiws;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OsiwsRequestFactoryTest {

    private static final String UPSERT_METADATA = """
            message:
              messageName: JmeDecreeDocumentCreatedEvent
              topicName: jme-decree-document-created
            operation:
              indexType: DecreeDocument
              indexMajorVersion: 1
            """;
    private static final String DELETE_METADATA = """
            indexType: DecreeDocument
            indexMajorVersion: 1
            """;

    @TempDir
    Path tempDir;

    private final OsiwsRequestFactory factory = new OsiwsRequestFactory(new OsiwsReferenceCsvParser());

    @Test
    void upsertWithCsvProducesGoldenRequestWithBomCrLfCommentsAndBlankVersion() throws Exception {
        Path yaml = write("job.yaml", UPSERT_METADATA);
        Path csv = write("ids.csv", "\uFEFFid,version\r\n12345,17\r\n# exported\r\n\r\n67890,\r\n");

        PreparedOsiwsRequest request = factory.upsert(yaml, csv);

        assertThat(request.yaml()).isEqualTo(fixture("upsert-request.yaml"));
        assertThat(request.referenceCount()).isEqualTo(2);
    }

    @Test
    void upsertWithReferencesInYamlProducesGoldenRequest() throws Exception {
        Path yaml = write("job.yaml", """
                message:
                  messageName: JmeDecreeDocumentCreatedEvent
                  topicName: jme-decree-document-created
                operation:
                  indexType: DecreeDocument
                  indexOperation: UPSERT
                  indexMajorVersion: 1
                originReferences:
                - id: 12345
                  version: 17
                - id: '67890'
                """);

        assertThat(factory.upsert(yaml, null).yaml()).isEqualTo(fixture("upsert-request.yaml"));
    }

    @Test
    void upsertCsvMayHaveOnlyTheIdColumn() throws Exception {
        Path yaml = write("job.yaml", UPSERT_METADATA);
        Path csv = write("ids.csv", "id\n12345\n67890\n");

        assertThat(references(factory.upsert(yaml, csv).yaml()))
                .containsExactly(Map.of("id", "12345"), Map.of("id", "67890"));
    }

    @Test
    void deleteWithCsvProducesGoldenRequest() throws Exception {
        Path yaml = write("job.yaml", DELETE_METADATA);
        Path csv = write("ids.csv", "id\n12345\n67890\n");

        PreparedOsiwsRequest request = factory.delete(yaml, csv);

        assertThat(request.yaml()).isEqualTo(fixture("delete-request.yaml"));
        assertThat(request.referenceCount()).isEqualTo(2);
    }

    @Test
    void omittedIndexMajorVersionIsLeftToTheIndexWriter() throws Exception {
        Path upsert = write("upsert.yaml", UPSERT_METADATA.replace("  indexMajorVersion: 1\n", ""));
        Path delete = write("delete.yaml", "indexType: DecreeDocument\n");
        Path csv = write("ids.csv", "id\n12345\n");

        assertThat(factory.upsert(upsert, csv).yaml()).doesNotContain("indexMajorVersion");
        assertThat(factory.delete(delete, csv).yaml()).doesNotContain("indexMajorVersion");
    }

    @Test
    void idsAndVersionsStayTextIncludingLeadingZerosAndKeepTheirOrder() throws Exception {
        Path yaml = write("job.yaml", UPSERT_METADATA + """
                originReferences:
                - id: 0042
                  version: 010
                - id: 'true'
                - id: 12345678901234567890
                """);
        Path csvYaml = write("csv-job.yaml", UPSERT_METADATA);
        Path csv = write("ids.csv", "id,version\n007,0017\n\" 1e3 \",1.50\n");

        assertThat(references(factory.upsert(yaml, null).yaml())).containsExactly(
                Map.of("id", "0042", "version", "010"),
                Map.of("id", "true"),
                Map.of("id", "12345678901234567890"));
        assertThat(references(factory.upsert(csvYaml, csv).yaml())).containsExactly(
                Map.of("id", "007", "version", "0017"),
                Map.of("id", "1e3", "version", "1.50"));
    }

    @Test
    void duplicateIdIsRejectedEvenWithDifferentVersions() throws Exception {
        Path yaml = write("job.yaml", UPSERT_METADATA);
        Path csv = write("ids.csv", "id,version\n12345,17\n67890,\n 12345 ,18\n");

        assertThatThrownBy(() -> factory.upsert(yaml, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Duplicate id '12345' in line 4")
                .hasMessageContaining("first in line 2")
                .hasMessageContaining("also with different versions");
    }

    @Test
    void duplicateIdInYamlIsRejected() throws Exception {
        Path yaml = write("job.yaml", DELETE_METADATA + """
                originReferences:
                - id: '12345'
                - id: 12345
                """);

        assertThatThrownBy(() -> factory.delete(yaml, null))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Duplicate id '12345' in originReferences[1]")
                .hasMessageContaining("first in originReferences[0]");
    }

    @Test
    void versionOnDeleteIsRejectedWithAHint() throws Exception {
        Path yaml = write("job.yaml", DELETE_METADATA);
        Path versionHeader = write("versions.csv", "id,version\n12345,17\n");
        Path versionValue = write("values.csv", "id\n12345,17\n");
        Path inlineVersion = write("inline.yaml", DELETE_METADATA + """
                originReferences:
                - id: '12345'
                  version: '17'
                """);

        assertThatThrownBy(() -> factory.delete(yaml, versionHeader))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("has a version column, but delete jobs have ids only");
        assertThatThrownBy(() -> factory.delete(yaml, versionValue))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Expected 1 column: id. Delete jobs have ids only, no version.");
        assertThatThrownBy(() -> factory.delete(inlineVersion, null))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Delete jobs have ids only. Remove version from originReferences[0]");
    }

    @Test
    void rollbackControlFieldsAreRejected() throws Exception {
        Path upsert = write("upsert.yaml", UPSERT_METADATA + "  allowRollback: true\n");
        Path delete = write("delete.yaml", DELETE_METADATA + "allowVersionRollback: true\n");
        Path csv = write("ids.csv", "id\n12345\n");

        assertThatThrownBy(() -> factory.upsert(upsert, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("operation.allowRollback")
                .hasMessageContaining("is not supported");
        assertThatThrownBy(() -> factory.delete(delete, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("allowVersionRollback")
                .hasMessageContaining("is not supported");
    }

    @Test
    void unknownFieldsAreRejectedWithTheAllowedFields() throws Exception {
        Path yaml = write("job.yaml", UPSERT_METADATA + "indexType: DecreeDocument\n");
        Path csv = write("ids.csv", "id\n12345\n");

        assertThatThrownBy(() -> factory.upsert(yaml, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Unknown YAML field indexType")
                .hasMessageContaining("Allowed: message, operation, originReferences.");
    }

    @Test
    void referencesMustComeFromExactlyOneSource() throws Exception {
        Path withReferences = write("with.yaml", DELETE_METADATA + "originReferences:\n- id: '1'\n");
        Path withoutReferences = write("without.yaml", DELETE_METADATA);
        Path csv = write("ids.csv", "id\n12345\n");
        Path emptyCsv = write("empty.csv", "id\n# nothing yet\n");

        assertThatThrownBy(() -> factory.delete(withReferences, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("defined in both");
        assertThatThrownBy(() -> factory.delete(withoutReferences, null))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("No originReferences provided");
        assertThatThrownBy(() -> factory.delete(withoutReferences, emptyCsv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("No originReferences provided");
    }

    @Test
    void indexMajorVersionMustBeAnUnquotedPositiveInteger() throws Exception {
        Path csv = write("ids.csv", "id\n12345\n");
        for (String value : List.of("'1'", "0", "-1", "1.5", "true")) {
            Path yaml = write("job.yaml", "indexType: DecreeDocument\nindexMajorVersion: " + value + "\n");

            assertThatThrownBy(() -> factory.delete(yaml, csv))
                    .isInstanceOf(OsiwsException.class)
                    .hasMessageContaining("indexMajorVersion must be a positive integer");
        }
    }

    @Test
    void upsertRequiresUpsertOperationAndMessage() throws Exception {
        Path csv = write("ids.csv", "id\n12345\n");
        Path otherOperation = write("other.yaml", UPSERT_METADATA + "  indexOperation: DELETE\n");
        Path withoutTopic = write("topic.yaml", UPSERT_METADATA.replace("  topicName: jme-decree-document-created\n", ""));

        assertThatThrownBy(() -> factory.upsert(otherOperation, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("operation.indexOperation must be UPSERT");
        assertThatThrownBy(() -> factory.upsert(withoutTopic, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("message.topicName must be set");
    }

    @Test
    void nonTextScalarIdsMustBeQuoted() throws Exception {
        Path yaml = write("job.yaml", DELETE_METADATA + "originReferences:\n- id: 1.50\n");

        assertThatThrownBy(() -> factory.delete(yaml, null))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("id in originReferences[0]")
                .hasMessageContaining("Quote it");
    }

    @Test
    void idsLongerThanTheIndexWriterLimitOrWithControlCharactersAreRejected() throws Exception {
        Path yaml = write("job.yaml", DELETE_METADATA);
        Path tooLong = write("long.csv", "id\n" + "x".repeat(OsiwsValidation.MAX_REFERENCE_LENGTH + 1) + "\n");
        Path control = write("control.csv", "id\n\"a\tb\"\n");
        Path blank = write("blank.csv", "id\n\"  \"\n");

        assertThatThrownBy(() -> factory.delete(yaml, tooLong))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("longer than 512 characters");
        assertThatThrownBy(() -> factory.delete(yaml, control))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("contains a control character");
        assertThatThrownBy(() -> factory.delete(yaml, blank))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void csvHeaderMustMatchExactly() throws Exception {
        Path yaml = write("job.yaml", UPSERT_METADATA);
        Path csv = write("ids.csv", "ID,Version\n12345,17\n");

        assertThatThrownBy(() -> factory.upsert(yaml, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("must use the exact header: id,version (or id)");
    }

    @Test
    void invalidOrAmbiguousYamlIsRejected() throws Exception {
        Path csv = write("ids.csv", "id\n12345\n");
        Path duplicateKey = write("duplicate.yaml", "indexType: A\nindexType: B\n");
        Path empty = write("empty.yaml", "# nothing\n");
        Path list = write("list.yaml", "- indexType: A\n");

        assertThatThrownBy(() -> factory.delete(duplicateKey, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("Could not parse YAML file");
        assertThatThrownBy(() -> factory.delete(empty, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("must contain a YAML object");
        assertThatThrownBy(() -> factory.delete(list, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("must contain a YAML object");
        assertThatThrownBy(() -> factory.delete(tempDir.resolve("missing.yaml"), csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void inlineJobsAboveSnakeYamlsDefaultLimitOfThreeMebibytesAreAccepted() throws Exception {
        StringBuilder content = new StringBuilder(UPSERT_METADATA).append("originReferences:\n");
        for (int index = 0; index < 100_000; index++) {
            content.append("- id: 'decree-document-").append(index).append("'\n  version: '17'\n");
        }
        assertThat(content.length()).isGreaterThan(3 * 1024 * 1024);
        Path yaml = write("job.yaml", content.toString());

        assertThat(factory.upsert(yaml, null).referenceCount()).isEqualTo(100_000);
    }

    @Test
    void malformedCsvIsRejectedWithTheFileName() throws Exception {
        Path yaml = write("job.yaml", DELETE_METADATA);
        Path csv = write("ids.csv", "id\n\"12345\"x\n");

        assertThatThrownBy(() -> factory.delete(yaml, csv))
                .isInstanceOf(OsiwsException.class)
                .hasMessageStartingWith("Could not parse CSV file " + csv + ": ")
                .hasMessageNotContaining("java.io.");
    }

    @SuppressWarnings("unchecked")
    private List<Object> references(String requestYaml) {
        Map<?, ?> request = YAMLMapper.builder().build().readValue(requestYaml, Map.class);
        return (List<Object>) request.get("originReferences");
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
