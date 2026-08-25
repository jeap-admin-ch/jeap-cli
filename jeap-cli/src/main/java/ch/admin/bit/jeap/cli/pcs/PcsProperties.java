package ch.admin.bit.jeap.cli.pcs;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Component
public class PcsProperties {

    private Limits limits = new Limits();

    @Getter
    @Setter(AccessLevel.PACKAGE)
    public static class Limits {
        private int maxTasksPerJob = 10_000;
        private int maxFieldLength = 2_000;
        private int maxProcessDataValuesPerTask = 100;
        private int maxProcessDataValuesPerJob = 10_000;
        private int maxRequestBytes = 10 * 1024 * 1024;
    }
}
