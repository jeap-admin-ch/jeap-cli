package ch.admin.bit.jeap.cli.pcs;

import java.util.List;
import java.util.UUID;

record ReevaluationProcess(String originProcessId) {
}

record ReevaluationRequest(String processTemplateName, List<ReevaluationProcess> processes) {
}

record ProcessDataValue(String key, String value, String role) {
}

record ProcessDataEntry(String originProcessId, List<ProcessDataValue> processData) {
}

record BackfillRequest(String processTemplateName, List<ProcessDataEntry> entries) {
}

record RelationPublicationRequest(List<UUID> relationIds) {
}

record ProcessDataCsvRow(String originProcessId, ProcessDataValue processData) {
}

record PreparedPcsRequest(String yaml, int taskCount) {
}
