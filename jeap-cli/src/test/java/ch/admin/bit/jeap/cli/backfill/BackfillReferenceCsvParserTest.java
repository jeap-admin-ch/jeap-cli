package ch.admin.bit.jeap.cli.backfill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class BackfillReferenceCsvParserTest {

    private final BackfillReferenceCsvParser parser = new BackfillReferenceCsvParser();

    @TempDir
    Path tempDir;

    @Test
    void parsesValidCsvWithTenReferences() throws Exception {
        Path csvFile = writeCsv("""
                id,version
                DOC-001,1
                DOC-002,1
                DOC-003,2
                DOC-004,1
                DOC-005,3
                DOC-006,1
                DOC-007,2
                DOC-008,1
                DOC-009,1
                DOC-010,4
                """);

        assertThat(parser.parse(csvFile))
                .hasSize(10)
                .containsExactly(
                        new ArchiveDataReferenceDto("DOC-001", 1),
                        new ArchiveDataReferenceDto("DOC-002", 1),
                        new ArchiveDataReferenceDto("DOC-003", 2),
                        new ArchiveDataReferenceDto("DOC-004", 1),
                        new ArchiveDataReferenceDto("DOC-005", 3),
                        new ArchiveDataReferenceDto("DOC-006", 1),
                        new ArchiveDataReferenceDto("DOC-007", 2),
                        new ArchiveDataReferenceDto("DOC-008", 1),
                        new ArchiveDataReferenceDto("DOC-009", 1),
                        new ArchiveDataReferenceDto("DOC-010", 4));
    }

    @Test
    void ignoresCommentAndBlankLines() throws Exception {
        Path csvFile = writeCsv("""
                id,version
                # exported references

                DOC-001,1
                   # another comment
                DOC-002,2
                """);

        assertThat(parser.parse(csvFile))
                .containsExactly(
                        new ArchiveDataReferenceDto("DOC-001", 1),
                        new ArchiveDataReferenceDto("DOC-002", 2));
    }

    @Test
    void failsForMissingHeader() throws Exception {
        Path csvFile = writeCsv("""
                DOC-001,1
                DOC-002,1
                """);

        assertThatThrownBy(() -> parser.parse(csvFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Error: CSV file must start with header line: id,version");
    }

    @Test
    void failsForInvalidVersionWithLineNumber() throws Exception {
        Path csvFile = writeCsv("""
                id,version
                DOC-001,1
                DOC-002,abc
                """);

        assertThatThrownBy(() -> parser.parse(csvFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Error: Invalid version 'abc' on line 3. Must be a positive integer.");
    }

    @Test
    void failsForEmptyIdWithLineNumber() throws Exception {
        Path csvFile = writeCsv("""
                id,version
                DOC-001,1
                ,2
                """);

        assertThatThrownBy(() -> parser.parse(csvFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Error: Empty id on line 3.");
    }

    @Test
    void failsForCsvRowWithExtraColumn() throws Exception {
        Path csvFile = writeCsv("""
                id,version
                DOC,001,1
                """);

        assertThatThrownBy(() -> parser.parse(csvFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Error: Invalid CSV row on line 2. Expected 2 columns: id,version.");
    }

    @Test
    void warnsForDuplicatesAndReturnsUniqueReferences(CapturedOutput output) throws Exception {
        Path csvFile = writeCsv("""
                id,version
                DOC-001,1
                DOC-002,1
                DOC-001,1
                DOC-001,2
                """);

        assertThat(parser.parse(csvFile))
                .containsExactly(
                        new ArchiveDataReferenceDto("DOC-001", 1),
                        new ArchiveDataReferenceDto("DOC-002", 1),
                        new ArchiveDataReferenceDto("DOC-001", 2));
        assertThat(output).contains("Warning: Duplicate reference id=DOC-001 version=1 on line 4. Will be sent once.");
    }

    private Path writeCsv(String content) throws Exception {
        Path csvFile = tempDir.resolve("references.csv");
        Files.writeString(csvFile, content);
        return csvFile;
    }
}
