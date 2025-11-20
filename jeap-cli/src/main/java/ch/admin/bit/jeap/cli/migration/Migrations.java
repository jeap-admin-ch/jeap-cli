package ch.admin.bit.jeap.cli.migration;

import ch.admin.bit.jeap.cli.migration.step.Step;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Migrations {

    public static void executeStep(Step step) throws Exception {
        logExecution(step);
        step.execute();
    }

    public static void executeOptionalStep(Step step) {
        try {
            logExecution(step);
            step.execute();
        } catch (Exception e) {
            log.warn("Optional migration step failed: {}", e.getMessage());
        }
    }

    private static void logExecution(Step step) {
        log.info("Executing step {}", step.name());
    }
}
