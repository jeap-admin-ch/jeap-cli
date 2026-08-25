package ch.admin.bit.jeap.cli.pcs;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class RelationCsvParser {

    private final PcsProperties properties;

    List<UUID> parse(Path file) {
        List<UUID> relationIds = new ArrayList<>();
        Set<UUID> uniqueIds = new HashSet<>();
        for (PcsCsvSupport.CsvRow row : PcsCsvSupport.read(file, List.of("relationId"), properties)) {
            UUID relationId = PcsValidation.uuid(row.value(0), "relationId", properties);
            if (!uniqueIds.add(relationId)) {
                throw new PcsException("Duplicate relationId '" + relationId
                        + "' in relation CSV record " + row.number() + ".");
            }
            relationIds.add(relationId);
        }
        return relationIds;
    }
}
