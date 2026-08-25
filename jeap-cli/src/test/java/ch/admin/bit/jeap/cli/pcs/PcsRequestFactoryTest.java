package ch.admin.bit.jeap.cli.pcs;

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

class PcsRequestFactoryTest {

    @TempDir
    Path tempDir;

    private final PcsProperties properties = new PcsProperties();
    private final PcsRequestFactory factory = new PcsRequestFactory(
            properties,
            new ReevaluationCsvParser(properties),
            new ProcessDataCsvParser(properties),
            new RelationCsvParser(properties));

    @Test
    void reevaluationCsvProducesGoldenRequestInDeterministicOrderWithBomAndCrLf() throws Exception {
        Path yaml = write("job.yaml", "process-template-name: assessmentProcess\n");
        Path csv = write("processes.csv", """
                \uFEFForiginProcessId\r
                assessment-4712\r
                # exported comment\r
                \r
                assessment-4711\r
                """);

        PreparedPcsRequest request = factory.reevaluation(yaml, csv);

        assertThat(request.yaml()).isEqualTo(fixture("reevaluation-request.yaml"));
        assertThat(request.taskCount()).isEqualTo(2);
    }

    @Test
    void processDataCsvHandlesBomAndGrouping() throws Exception {
        Path yaml = write("job.yaml", "process-template-name: assessmentProcess\n");
        Path csv = write("process-data.csv", """
                \uFEFForiginProcessId,key,value,role
                assessment-4712,assessmentId,a-789,
                assessment-4711,assessmentId,a-123,
                assessment-4711,assessmentArtefactId,"art-456",FinalVersion
                """);

        PreparedPcsRequest request = factory.backfill(yaml, csv);

        assertThat(request.yaml()).isEqualTo(fixture("backfill-request.yaml"));
        assertThat(request.taskCount()).isEqualTo(2);
    }

    @Test
    void processDataCsvParsesQuotedEmbeddedNewlineAsOneLogicalRecord() throws Exception {
        Path yaml = write("job.yaml", "process-template-name: assessmentProcess\n");
        Path csv = write("process-data.csv", """
                originProcessId,key,value,role
                assessment-4711,note,"first,line
                second line",
                """);

        PreparedPcsRequest request = factory.backfill(yaml, csv);
        Map<?, ?> parsed = YAMLMapper.builder().build().readValue(request.yaml(), Map.class);
        List<?> entries = (List<?>) parsed.get("entries");
        Map<?, ?> entry = (Map<?, ?>) entries.getFirst();
        List<?> processData = (List<?>) entry.get("process-data");
        Map<?, ?> value = (Map<?, ?>) processData.getFirst();

        assertThat(value.get("value")).isEqualTo("first,line\nsecond line");
        assertThat(request.taskCount()).isOne();
    }

    @Test
    void relationCsvNormalizesAndSortsUuids() throws Exception {
        Path csv = write("relations.csv", """
                relationId
                019C8C72-7B42-7A04-9443-BF8EC98CE871
                019c8c72-6fd1-7f25-a9a1-3b3d51fbb321
                """);

        PreparedPcsRequest request = factory.relationPublication(null, csv);

        assertThat(request.yaml()).isEqualTo(fixture("relation-publication-request.yaml"));
        assertThat(request.taskCount()).isEqualTo(2);
    }

    @Test
    void relationOrderingUsesTheCanonicalPcsUuidOrder() throws Exception {
        Path csv = write("uuid-order.csv", """
                relationId
                00000000-0000-0000-0000-000000000000
                ffffffff-ffff-ffff-ffff-ffffffffffff
                """);

        PreparedPcsRequest request = factory.relationPublication(null, csv);

        assertThat(request.yaml()).containsSubsequence(
                "ffffffff-ffff-ffff-ffff-ffffffffffff",
                "00000000-0000-0000-0000-000000000000");
    }

    @Test
    void completeYamlIsNormalizedToTheSameGoldenRequests() throws Exception {
        assertThat(factory.reevaluation(write("reevaluation.yaml", fixture("reevaluation-request.yaml")), null).yaml())
                .isEqualTo(fixture("reevaluation-request.yaml"));
        assertThat(factory.backfill(write("backfill.yaml", fixture("backfill-request.yaml")), null).yaml())
                .isEqualTo(fixture("backfill-request.yaml"));
        assertThat(factory.relationPublication(
                write("relations.yaml", fixture("relation-publication-request.yaml")), null).yaml())
                .isEqualTo(fixture("relation-publication-request.yaml"));
    }

    @Test
    void embeddedEntriesAndCsvAreMutuallyExclusive() throws Exception {
        Path yaml = write("job.yaml", fixture("reevaluation-request.yaml"));
        Path csv = write("processes.csv", "originProcessId\nassessment-4711\n");

        assertThatThrownBy(() -> factory.reevaluation(yaml, csv))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("processes")
                .hasMessageContaining("both YAML and --processes-csv");
    }

    @Test
    void wrongHeaderIsRejected() throws Exception {
        Path yaml = write("job.yaml", "process-template-name: assessmentProcess\n");
        Path csv = write("processes.csv", "processId\nassessment-4711\n");

        assertThatThrownBy(() -> factory.reevaluation(yaml, csv))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("originProcessId");
    }

    @Test
    void whitespaceAroundHeaderIsRejected() throws Exception {
        Path yaml = write("job.yaml", "process-template-name: assessmentProcess\n");
        Path csv = write("processes.csv", " originProcessId \nassessment-4711\n");

        assertThatThrownBy(() -> factory.reevaluation(yaml, csv))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("exact header");
    }

    @Test
    void invalidRelationUuidIsRejected() throws Exception {
        Path csv = write("relations.csv", "relationId\nnot-a-uuid\n");

        assertThatThrownBy(() -> factory.relationPublication(null, csv))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("Invalid relationId 'not-a-uuid'");
    }

    @Test
    void configuredTaskAndFieldLimitsAreEnforced() throws Exception {
        properties.getLimits().setMaxTasksPerJob(1);
        Path yaml = write("job.yaml", "process-template-name: assessmentProcess\n");
        Path csv = write("processes.csv", "originProcessId\na\nb\n");

        assertThatThrownBy(() -> factory.reevaluation(yaml, csv))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("maximum of 1 records");

        properties.getLimits().setMaxTasksPerJob(10);
        properties.getLimits().setMaxFieldLength(3);
        Path shortTemplateYaml = write("short-job.yaml", "process-template-name: abc\n");
        assertThatThrownBy(() -> factory.reevaluation(
                shortTemplateYaml, write("one.csv", "originProcessId\nlong\n")))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("originProcessId")
                .hasMessageContaining("UTF-8 length of 3 bytes");
    }

    @Test
    void configuredCsvRecordLimitIsEnforced() throws Exception {
        properties.getLimits().setMaxTasksPerJob(1);
        Path yaml = write("job.yaml", "process-template-name: assessmentProcess\n");
        Path csv = write("processes.csv", "originProcessId\na\nb\n");

        assertThatThrownBy(() -> factory.reevaluation(yaml, csv))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("maximum of 1 records");
    }

    @Test
    void configuredCsvInputByteLimitIsEnforcedBeforeParsing() throws Exception {
        properties.getLimits().setMaxRequestBytes(25);
        Path yaml = write("job.yaml", "process-template-name: a\n");
        Path csv = write("processes.csv", "originProcessId\nassessment-4711\n");

        assertThatThrownBy(() -> factory.reevaluation(yaml, csv))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("maximum input size of 25 bytes");
    }

    @Test
    void normalizedDuplicatesInEveryCsvFormatAreRejected() throws Exception {
        Path metadata = write("job.yaml", "process-template-name: assessmentProcess\n");
        assertThatThrownBy(() -> factory.reevaluation(metadata,
                write("processes.csv", "originProcessId\n process-1 \nprocess-1\n")))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("Duplicate originProcessId");
        assertThatThrownBy(() -> factory.backfill(metadata,
                write("data.csv", "originProcessId,key,value,role\np,k,v,\n p , k , v ,\n")))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("Duplicate process-data value");
        assertThatThrownBy(() -> factory.relationPublication(null,
                write("relations-duplicate.csv", "relationId\n019c8c72-6fd1-7f25-a9a1-3b3d51fbb321\n"
                        + "019C8C72-6FD1-7F25-A9A1-3B3D51FBB321\n")))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("Duplicate relationId");
    }

    @Test
    void utf8ByteLengthAndPerTaskProcessDataLimitAreEnforced() throws Exception {
        properties.getLimits().setMaxFieldLength(4);
        Path metadata = write("utf8.yaml", "process-template-name: ok\n");
        assertThatThrownBy(() -> factory.reevaluation(metadata,
                write("utf8.csv", "originProcessId\näää\n")))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("UTF-8 length of 4 bytes");

        properties.getLimits().setMaxFieldLength(2_000);
        properties.getLimits().setMaxProcessDataValuesPerTask(1);
        assertThatThrownBy(() -> factory.backfill(metadata, write("too-many-data.csv", """
                originProcessId,key,value,role
                p,a,1,
                p,b,2,
                """)))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("maximum of 1 values")
                .hasMessageContaining("origin-process-id 'p'");
    }

    @Test
    void perJobProcessDataAndCanonicalRequestByteLimitsAreEnforced() throws Exception {
        properties.getLimits().setMaxProcessDataValuesPerJob(1);
        Path metadata = write("job.yaml", "process-template-name: assessmentProcess\n");
        assertThatThrownBy(() -> factory.backfill(metadata, write("job-data.csv", """
                originProcessId,key,value,role
                p1,a,1,
                p2,b,2,
                """)))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("per-job maximum of 1");

        properties.getLimits().setMaxRequestBytes(90);
        assertThatThrownBy(() -> factory.relationPublication(null, write("large-relations.csv", """
                relationId
                019c8c72-6fd1-7f25-a9a1-3b3d51fbb321
                019c8c72-7b42-7a04-9443-bf8ec98ce871
                """)))
                .isInstanceOf(PcsException.class)
                .hasMessageContaining("Canonical YAML request")
                .hasMessageContaining("90 bytes");
    }

    private Path write(String name, String content) throws Exception {
        return Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private String fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/pcs/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
