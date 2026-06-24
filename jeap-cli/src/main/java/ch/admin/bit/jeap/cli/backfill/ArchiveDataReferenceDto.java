package ch.admin.bit.jeap.cli.backfill;

import java.util.Objects;

public final class ArchiveDataReferenceDto {

    private final String id;
    private final Integer version;

    public ArchiveDataReferenceDto(String id, Integer version) {
        this.id = id;
        this.version = version;
    }

    public String id() {
        return id;
    }

    public Integer version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArchiveDataReferenceDto that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}
