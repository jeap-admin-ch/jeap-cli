package ch.admin.bit.jeap.cli.migration;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.migration.step.maven.RunMaven;
import ch.admin.bit.jeap.cli.process.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

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

    /**
     * Builds the migrated project with {@code install} to verify that it still compiles and its tests pass.
     * A failing build fails the migration.
     */
    public static void executeFinalVerification(Path root, ProcessExecutor processExecutor) throws Exception {
        log.info("Migration steps completed. Running final verification (mvnw install)...");
        executeStep(new RunMaven(root, processExecutor, "install"));
        log.info("Final verification successful.");
    }

    private static void logExecution(Step step) {
        log.info(":: Executing step {}", step.name());
    }
}
