package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.migration.step.maven.MavenCommandException;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;

import static ch.admin.bit.jeap.cli.migration.Migrations.executeStep;

/**
 * Executes migration steps and, optionally, retries Maven failures with Copilot-powered auto-fixes.
 * Successfully completed steps are not executed again on retries.
 */
@Slf4j
public class RetryingMavenStepExecutor {

    private final MavenFailureAutoFixer mavenFailureAutoFixer;
    private final String executionName;

    public RetryingMavenStepExecutor(MavenFailureAutoFixer mavenFailureAutoFixer) {
        this(mavenFailureAutoFixer, "Migration");
    }

    public RetryingMavenStepExecutor(MavenFailureAutoFixer mavenFailureAutoFixer, String executionName) {
        this.mavenFailureAutoFixer = mavenFailureAutoFixer;
        this.executionName = executionName;
    }

    public void execute(Path root, List<Step> steps, boolean autoFixMavenFailures, int maxAutoFixRetries) throws Exception {
        int maxRetries = Math.max(0, maxAutoFixRetries);
        int attempt = 0;
        int nextStepIndex = 0;

        if (autoFixMavenFailures && !mavenFailureAutoFixer.prepare(root)) {
            throw new IllegalStateException("Copilot CLI is not available. Aborting " + executionName + ".");
        }

        while (true) {
            try {
                runFromStep(steps, nextStepIndex);
                if (autoFixMavenFailures && attempt > 0) {
                    log.info("{} completed successfully after {} auto-fix iteration(s).", executionName, attempt);
                }
                return;
            } catch (MavenFailureAtStepException e) {
                MavenCommandException mavenFailure = e.error();
                nextStepIndex = e.stepIndex();
                if (!autoFixMavenFailures) {
                    throw mavenFailure;
                }
                if (attempt >= maxRetries) {
                    log.error("Maven still failing after {} auto-fix iteration(s). Aborting migration.", maxRetries);
                    throw mavenFailure;
                }
                attempt++;
                log.warn("Maven migration failed in iteration {} at step '{}' with command '{}'.",
                        attempt, steps.get(nextStepIndex).name(), mavenFailure.getCommand());
                if (!mavenFailure.getOutput().isBlank()) {
                    log.warn("Maven error output:\n{}", mavenFailure.getOutput());
                }
                boolean fixed = mavenFailureAutoFixer.tryFix(root, mavenFailure, attempt, maxRetries);
                if (!fixed) {
                    log.error("No usable Copilot fix found for iteration {}. Aborting migration.", attempt);
                    throw mavenFailure;
                }
                log.info("Applied auto-fix for iteration {}. Retrying migration.", attempt);
            }
        }
    }

    private void runFromStep(List<Step> steps, int startIndex) throws Exception {
        for (int i = startIndex; i < steps.size(); i++) {
            try {
                executeStep(steps.get(i));
            } catch (MavenCommandException e) {
                throw new MavenFailureAtStepException(i, e);
            }
        }
    }

    private static final class MavenFailureAtStepException extends Exception {
        private final int stepIndex;
        private final MavenCommandException error;

        private MavenFailureAtStepException(int stepIndex, MavenCommandException error) {
            super(error);
            this.stepIndex = stepIndex;
            this.error = error;
        }

        private int stepIndex() {
            return stepIndex;
        }

        private MavenCommandException error() {
            return error;
        }
    }
}
