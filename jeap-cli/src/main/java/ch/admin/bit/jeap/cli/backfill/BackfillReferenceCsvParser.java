package ch.admin.bit.jeap.cli.backfill;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BackfillReferenceCsvParser {

    private static final String EXPECTED_HEADER = "id,version";
    private static final CSVFormat DATA_FORMAT = CSVFormat.DEFAULT.builder()
            .setTrim(true)
            .build();

    public List<ArchiveDataReferenceDto> parse(Path csvFile) throws IOException {
        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !EXPECTED_HEADER.equals(stripBom(lines.getFirst()).strip())) {
            throw new IllegalArgumentException("Error: CSV file must start with header line: id,version");
        }

        Map<ReferenceKey, ArchiveDataReferenceDto> references = new LinkedHashMap<>();
        for (int index = 1; index < lines.size(); index++) {
            int lineNumber = index + 1;
            String line = lines.get(index);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }

            CSVRecord record = parseRecord(line);
            if (record.size() != 2) {
                throw new IllegalArgumentException("Error: Invalid CSV row on line " + lineNumber
                        + ". Expected 2 columns: id,version.");
            }
            String id = value(record, 0);
            String versionValue = value(record, 1);

            if (id.isBlank()) {
                throw new IllegalArgumentException("Error: Empty id on line " + lineNumber + ".");
            }

            int version = parseVersion(versionValue, lineNumber);
            ReferenceKey key = new ReferenceKey(id, version);
            if (references.containsKey(key)) {
                System.out.println("Warning: Duplicate reference id=" + id + " version=" + version + " on line "
                        + lineNumber + ". Will be sent once.");
            } else {
                references.put(key, new ArchiveDataReferenceDto(id, version));
            }
        }

        return new ArrayList<>(references.values());
    }

    private CSVRecord parseRecord(String line) throws IOException {
        return DATA_FORMAT.parse(new StringReader(line)).getRecords().getFirst();
    }

    private String value(CSVRecord record, int index) {
        if (record.size() <= index) {
            return "";
        }
        return record.get(index);
    }

    private int parseVersion(String value, int lineNumber) {
        try {
            int version = Integer.parseInt(value);
            if (version < 1) {
                throw new NumberFormatException();
            }
            return version;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Error: Invalid version '" + value + "' on line " + lineNumber
                    + ". Must be a positive integer.");
        }
    }

    private String stripBom(String line) {
        if (line.startsWith("\uFEFF")) {
            return line.substring(1);
        }
        return line;
    }

    private static final class ReferenceKey {
        private final String id;
        private final int version;

        private ReferenceKey(String id, int version) {
            this.id = id;
            this.version = version;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ReferenceKey that)) {
                return false;
            }
            return version == that.version && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return 31 * id.hashCode() + version;
        }
    }
}
