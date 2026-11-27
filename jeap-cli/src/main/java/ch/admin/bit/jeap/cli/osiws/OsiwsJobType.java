package ch.admin.bit.jeap.cli.osiws;

import java.util.Arrays;

public enum OsiwsJobType {
    UPSERT("upsert", "Upsert", "upsertjob#create"),
    DELETE("delete", "Delete", "deletejob#create");

    static final String REPORT_ROLE = "jobreport#read";

    private final String cliValue;
    private final String displayName;
    private final String submitRole;

    OsiwsJobType(String cliValue, String displayName, String submitRole) {
        this.cliValue = cliValue;
        this.displayName = displayName;
        this.submitRole = submitRole;
    }

    public String cliValue() {
        return cliValue;
    }

    String endpointSegment() {
        return cliValue + "-jobs";
    }

    String displayName() {
        return displayName;
    }

    String submitRole() {
        return submitRole;
    }

    public static OsiwsJobType fromCliValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.cliValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new OsiwsException("Invalid job type '" + value + "'. Expected upsert or delete."));
    }
}
