package ch.admin.bit.jeap.cli.backfill;

import java.util.List;

public final class BackfillJobRequestDto {

    private final String message;
    private final String topic;
    private final Integer numOfRetry;
    private final List<ArchiveDataReferenceDto> archiveDataReferences;

    public BackfillJobRequestDto(String message, String topic, Integer numOfRetry,
                                 List<ArchiveDataReferenceDto> archiveDataReferences) {
        this.message = message;
        this.topic = topic;
        this.numOfRetry = numOfRetry;
        this.archiveDataReferences = List.copyOf(archiveDataReferences);
    }

    public String message() {
        return message;
    }

    public String topic() {
        return topic;
    }

    public Integer numOfRetry() {
        return numOfRetry;
    }

    public List<ArchiveDataReferenceDto> archiveDataReferences() {
        return archiveDataReferences;
    }
}
