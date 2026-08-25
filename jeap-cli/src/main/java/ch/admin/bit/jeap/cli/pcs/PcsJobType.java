package ch.admin.bit.jeap.cli.pcs;

import java.util.Arrays;

public enum PcsJobType {
    REEVALUATION("reevaluation"),
    BACKFILL("backfill"),
    RELATION_PUBLICATION("relation-publication");

    private final String cliValue;

    PcsJobType(String cliValue) {
        this.cliValue = cliValue;
    }

    public String endpointSegment() {
        return cliValue;
    }

    public static PcsJobType fromCliValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.cliValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new PcsException("Invalid job type '" + value
                        + "'. Expected reevaluation, backfill or relation-publication."));
    }
}
