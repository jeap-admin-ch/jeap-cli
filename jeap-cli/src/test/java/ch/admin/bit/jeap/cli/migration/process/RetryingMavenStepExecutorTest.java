package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.migration.step.maven.MavenCommandException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryingMavenStepExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void testAutoFixResumeSkipsAlreadySuccessfulSteps() throws Exception {
        RecordingAutoFixer autoFixer = new RecordingAutoFixer(true, true);
        RetryingMavenStepExecutor executor = new RetryingMavenStepExecutor(autoFixer, "Test migration");

        CountingStep first = new CountingStep("first", null);
        AtomicInteger secondAttempts = new AtomicInteger();
        CountingStep second = new CountingStep("second",
                () -> secondAttempts.incrementAndGet() == 1
                        ? new MavenCommandException(1, "mvn test", "[ERROR] failure")
                        : null);
        CountingStep third = new CountingStep("third", null);

        executor.execute(tempDir, List.of(first, second, third), true, 2);

        assertEquals(1, first.invocations(), "First step must not be repeated after it already succeeded");
        assertEquals(2, second.invocations(), "Failed step should be retried");
        assertEquals(1, third.invocations(), "Remaining steps should run once after failure is fixed");
        assertEquals(1, autoFixer.preparations, "Auto-fixer should be prepared once");
        assertEquals(1, autoFixer.invocations, "Auto-fixer should be invoked once");
    }

    @Test
    void testWithoutAutoFixMavenFailureIsNotRetried() {
        RecordingAutoFixer autoFixer = new RecordingAutoFixer(true, true);
        RetryingMavenStepExecutor executor = new RetryingMavenStepExecutor(autoFixer, "Test migration");
        CountingStep failingStep = new CountingStep("failing",
                () -> new MavenCommandException(1, "mvn install", "[ERROR] boom"));

        assertThrows(MavenCommandException.class,
                () -> executor.execute(tempDir, List.of(failingStep), false, 3));
        assertEquals(1, failingStep.invocations(), "Step should run once without retries");
        assertEquals(0, autoFixer.preparations, "Auto-fixer preparation should not run when disabled");
        assertEquals(0, autoFixer.invocations, "Auto-fixer should not run when disabled");
    }

    private static final class CountingStep implements Step {
        private final String name;
        private final FailureSupplier failureSupplier;
        private int invocations;

        private CountingStep(String name, FailureSupplier failureSupplier) {
            this.name = name;
            this.failureSupplier = failureSupplier;
        }

        @Override
        public void execute() throws Exception {
            invocations++;
            if (failureSupplier == null) {
                return;
            }
            Exception failure = failureSupplier.get();
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public String name() {
            return name;
        }

        private int invocations() {
            return invocations;
        }
    }

    private interface FailureSupplier {
        Exception get();
    }

    private static final class RecordingAutoFixer implements MavenFailureAutoFixer {
        private final boolean fixResult;
        private final boolean prepareResult;
        private int invocations;
        private int preparations;

        private RecordingAutoFixer(boolean fixResult, boolean prepareResult) {
            this.fixResult = fixResult;
            this.prepareResult = prepareResult;
        }

        @Override
        public boolean prepare(Path root) {
            preparations++;
            return prepareResult;
        }

        @Override
        public boolean tryFix(Path root, MavenCommandException failure, int attempt, int maxRetries) {
            invocations++;
            return fixResult;
        }
    }
}
