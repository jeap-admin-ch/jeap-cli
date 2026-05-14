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


}
