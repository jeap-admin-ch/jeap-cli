package ch.admin.bit.jeap.cli.pcs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PcsCsvSupport {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setCommentMarker('#')
            .setIgnoreEmptyLines(true)
            .build();

    private PcsCsvSupport() {
    }

    static List<CsvRow> read(Path file, List<String> expectedHeaders, PcsProperties properties) {
        String csv;
        try {
            long maximumBytes = properties.getLimits().getMaxRequestBytes();
            if (Files.size(file) > maximumBytes) {
                throw new PcsException("CSV file " + file + " exceeds the configured maximum input size of "
                        + maximumBytes + " bytes.");
            }
            csv = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new PcsException("File not found: " + file, e);
        } catch (IOException e) {
            throw new PcsException("Could not read CSV file " + file + ": " + e.getMessage(), e);
        }

        if (csv.startsWith("\uFEFF")) {
            csv = csv.substring(1);
        }

        try (CSVParser parser = FORMAT.parse(new StringReader(csv))) {
            if (!parser.getHeaderNames().equals(expectedHeaders)) {
                throw new PcsException("CSV file " + file + " must use the exact header: "
                        + String.join(",", expectedHeaders));
            }

            List<CsvRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (record.size() != expectedHeaders.size()) {
                    throw new PcsException("Invalid CSV record " + record.getRecordNumber() + " in " + file
                            + ". Expected " + expectedHeaders.size() + " columns: "
                            + String.join(",", expectedHeaders) + ".");
                }
                rows.add(new CsvRow(record.getRecordNumber(), record.toList()));
                int maximum = properties.getLimits().getMaxTasksPerJob();
                if (rows.size() > maximum) {
                    throw new PcsException("CSV file " + file + " exceeds the PCS maximum of "
                            + maximum + " records.");
                }
            }
            return rows;
        } catch (PcsException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw new PcsException("Could not parse CSV file " + file + ": " + e.getMessage(), e);
        }
    }

    record CsvRow(long number, List<String> values) {
        String value(int index) {
            return values.get(index);
        }
    }
}
