package ch.admin.bit.jeap.cli.osiws;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads origin references from a CSV file with the header {@code id,version} or {@code id} (upsert) or {@code id}
 * (delete). Blank lines and lines starting with {@code #} are ignored, the order of the records is kept.
 */
@Component
class OsiwsReferenceCsvParser {

    private static final List<String> ID_ONLY = List.of("id");
    private static final List<String> ID_AND_VERSION = List.of("id", "version");
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setCommentMarker('#')
            .setIgnoreEmptyLines(true)
            .get();

    List<OriginReference> parse(Path file, OsiwsJobType type) {
        String csv = read(file);
        try (CSVParser parser = FORMAT.parse(new StringReader(csv))) {
            List<String> header = parser.getHeaderNames();
            validateHeader(file, header, type);

            List<OriginReference> references = new ArrayList<>();
            Map<String, String> firstLocationById = new HashMap<>();
            for (CSVRecord record : parser) {
                String location = "line " + record.getParser().getCurrentLineNumber() + " of " + file;
                if (record.size() != header.size()) {
                    throw new OsiwsException("Invalid CSV record in " + location + ". Expected " + header.size()
                            + (header.size() == 1 ? " column: " : " columns: ") + String.join(",", header) + "."
                            + (type == OsiwsJobType.DELETE ? " Delete jobs have ids only, no version." : ""));
                }
                String id = OsiwsValidation.referenceId(record.get(0), location);
                String version = header.size() == 2 ? OsiwsValidation.referenceVersion(record.get(1), location) : null;
                OsiwsValidation.requireUnique(firstLocationById, id, location);
                references.add(new OriginReference(id, version));
            }
            return references;
        } catch (OsiwsException e) {
            throw e;
        } catch (UncheckedIOException e) {
            // malformed records, e.g. text after a closing quote
            throw new OsiwsException("Could not parse CSV file " + file + ": " + e.getCause().getMessage(), e);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new OsiwsException("Could not parse CSV file " + file + ": " + e.getMessage(), e);
        }
    }

    private void validateHeader(Path file, List<String> header, OsiwsJobType type) {
        if (type == OsiwsJobType.DELETE) {
            if (header.equals(ID_AND_VERSION)) {
                throw new OsiwsException("CSV file " + file + " has a version column, but delete jobs have ids only. "
                        + "Use the header: id");
            }
            if (!header.equals(ID_ONLY)) {
                throw new OsiwsException("CSV file " + file + " must use the exact header: id");
            }
        } else if (!header.equals(ID_AND_VERSION) && !header.equals(ID_ONLY)) {
            throw new OsiwsException("CSV file " + file + " must use the exact header: id,version (or id)");
        }
    }

    private String read(Path file) {
        try {
            String csv = Files.readString(file, StandardCharsets.UTF_8);
            return csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        } catch (NoSuchFileException e) {
            throw new OsiwsException("File not found: " + file, e);
        } catch (CharacterCodingException e) {
            throw new OsiwsException("CSV file " + file + " is not valid UTF-8.", e);
        } catch (IOException e) {
            throw new OsiwsException("Could not read CSV file " + file + ": " + e.getMessage(), e);
        }
    }
}
