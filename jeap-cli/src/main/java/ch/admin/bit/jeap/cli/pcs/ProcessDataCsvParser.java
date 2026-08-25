package ch.admin.bit.jeap.cli.pcs;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
class ProcessDataCsvParser {

    private final PcsProperties properties;

    List<ProcessDataCsvRow> parse(Path file) {
        List<ProcessDataCsvRow> rows = new ArrayList<>();
        Set<ProcessDataCsvRow> uniqueRows = new HashSet<>();
        for (PcsCsvSupport.CsvRow row : PcsCsvSupport.read(
                file, List.of("originProcessId", "key", "value", "role"), properties)) {
            ProcessDataCsvRow processDataRow = new ProcessDataCsvRow(
                    PcsValidation.requiredString(row.value(0), "originProcessId", properties),
                    new ProcessDataValue(
                            PcsValidation.requiredString(row.value(1), "key", properties),
                            PcsValidation.requiredString(row.value(2), "value", properties),
                            PcsValidation.optionalString(row.value(3), "role", properties)));
            if (!uniqueRows.add(processDataRow)) {
                throw new PcsException("Duplicate process-data value in CSV record " + row.number()
                        + " for originProcessId '" + processDataRow.originProcessId() + "'.");
            }
            rows.add(processDataRow);
        }
        return rows;
    }
}
