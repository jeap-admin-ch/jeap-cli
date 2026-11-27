package ch.admin.bit.jeap.cli.osiws;

import tools.jackson.core.JacksonException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Collects the pages of a job report into one YAML document: the first page with the references of every page and
 * without the page block. Fails unless the pages cover exactly the job's total-references.
 */
final class OsiwsReportMerger {

    private static final String ORIGIN_REFERENCES = "originReferences";

    private final OsiwsJobType type;
    private final UUID jobId;
    private final List<Object> references = new ArrayList<>();
    private Map<String, Object> firstPage;
    private int totalReferences;
    private String requestedCursor;

    OsiwsReportMerger(OsiwsJobType type, UUID jobId) {
        this.type = type;
        this.jobId = jobId;
    }

    /**
     * @return the cursor of the next page, or null if the page was the last one
     */
    String add(String pageYaml) {
        Map<String, Object> page = parse(pageYaml);
        requireJob(page);
        List<?> pageReferences = OsiwsYaml.list(page.get(ORIGIN_REFERENCES), ORIGIN_REFERENCES + " of the report");
        Map<String, Object> pageBlock = OsiwsYaml.map(page.get("page"), "page of the report");
        int total = totalReferences(page);
        if (firstPage == null) {
            firstPage = page;
            totalReferences = total;
        } else if (total != totalReferences) {
            throw inconsistent("total-references changed from " + totalReferences + " to " + total);
        }

        references.addAll(pageReferences);
        if (references.size() > totalReferences) {
            throw inconsistent("the pages contain more than the " + totalReferences + " references of the job");
        }
        Object next = pageBlock.get("next-cursor");
        String nextCursor = next == null ? null : String.valueOf(next);
        if (nextCursor != null && (nextCursor.isBlank() || pageReferences.isEmpty()
                || nextCursor.equals(requestedCursor))) {
            throw inconsistent("the next-cursor '" + nextCursor + "' does not advance");
        }
        requestedCursor = nextCursor;
        return nextCursor;
    }

    int referenceCount() {
        return references.size();
    }

    int totalReferences() {
        return totalReferences;
    }

    String mergedYaml() {
        if (firstPage == null || references.size() != totalReferences) {
            throw new OsiwsException("The report is incomplete: the index writer returned " + references.size()
                    + " of " + totalReferences + " references.");
        }
        Map<String, Object> merged = new LinkedHashMap<>(firstPage);
        merged.put(ORIGIN_REFERENCES, references);
        merged.remove("page");
        return OsiwsYaml.write(merged);
    }

    private Map<String, Object> parse(String pageYaml) {
        try {
            return OsiwsYaml.map(OsiwsYaml.read(pageYaml), "The report");
        } catch (JacksonException e) {
            throw new OsiwsException("The index writer returned a report that is not valid YAML: "
                    + e.getOriginalMessage(), e);
        }
    }

    private void requireJob(Map<String, Object> page) {
        Object reportedJobId = page.get("job-id");
        if (reportedJobId == null || !jobId.toString().equalsIgnoreCase(String.valueOf(reportedJobId))) {
            throw inconsistent("it is for job-id '" + reportedJobId + "' instead of " + jobId);
        }
        Object reportedType = page.get("job-type");
        if (!type.cliValue().equals(reportedType)) {
            throw inconsistent("it is for job-type '" + reportedType + "' instead of " + type.cliValue());
        }
    }

    private static int totalReferences(Map<String, Object> page) {
        Object total = OsiwsYaml.map(page.get("summary"), "summary of the report").get("total-references");
        if (!(total instanceof Integer count) || count < 0) {
            throw inconsistent("summary.total-references is missing or invalid");
        }
        return count;
    }

    private static OsiwsException inconsistent(String detail) {
        return new OsiwsException("The index writer returned an inconsistent report page: " + detail + ".");
    }
}
