package ch.admin.bit.jeap.cli.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemProcessExecutorEnvTest {

    @Test
    void testEnvironmentVariablesArePassed(@TempDir Path tempDir) throws IOException, InterruptedException {
        SystemProcessExecutor executor = new SystemProcessExecutor();
        // Use a simple command to print an environment variable
        // On Linux/macOS we can use 'sh -c echo $TEST_VAR'
        
        // We can't easily set an environment variable in the current process in Java reliably (System.setProperty is NOT System.getenv)
        // But we can check if SOME existing environment variable is passed.
        // Or we can assume that if we add it to the builder, it's there.
        
        // Let's try to see if 'PATH' is there, which should always be there.
        ProcessExecutionResult result = executor.executeAndCapture(List.of("sh", "-c", "echo $PATH"), tempDir);
        
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.combinedOutput()).contains(System.getenv("PATH"));
    }
}
