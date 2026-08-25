package ch.admin.bit.jeap.cli.pcs;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
class ReevaluationCsvParser {

    private final PcsProperties properties;

    List<ReevaluationProcess> parse(Path file) {
        List<ReevaluationProcess> processes = new java.util.ArrayList<>();
        Set<String> processIds = new HashSet<>();
        for (PcsCsvSupport.CsvRow row : PcsCsvSupport.read(file, List.of("originProcessId"), properties)) {
            String processId = PcsValidation.requiredString(row.value(0), "originProcessId", properties);
            if (!processIds.add(processId)) {
                throw new PcsException("Duplicate originProcessId '" + processId
                        + "' in reevaluation CSV record " + row.number() + ".");
            }
            processes.add(new ReevaluationProcess(processId));
        }
        return processes;
    }
}
