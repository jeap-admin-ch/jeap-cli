package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.migration.step.Step;
import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunMavenTest {

    @TempDir
    Path tempDir;

    @Test
    void testSuccessfulMavenExecution() throws Exception {
        // Given a fake process executor that returns success
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);
        Step runMaven = new RunMaven(tempDir, List.of("clean", "install"), fakeExecutor);

        // When executing the step
        runMaven.execute();

        // Then the Maven command should have been executed
        assertEquals(1, fakeExecutor.getExecutionCount(),
                "Should have executed one command");

        FakeProcessExecutor.ExecutedCommand executed = fakeExecutor.getLastExecutedCommand();
        assertEquals(List.of("mvn", "clean", "install"), executed.command(),
                "Should have executed mvn with correct arguments");
        assertEquals(tempDir, executed.workingDirectory(),
                "Should have executed in correct directory");
    }

    @Test
    void testFailedMavenExecution() {
        // Given a fake process executor that returns failure
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(1);
        Step runMaven = new RunMaven(tempDir, List.of("test"), fakeExecutor);

        // When executing the step
        // Then it should throw an IOException
        IOException exception = assertThrows(IOException.class, runMaven::execute,
                "Failed Maven execution should throw IOException");

        assertTrue(exception.getMessage().contains("Maven command failed with exit code 1"),
                "Exception message should contain exit code");
        assertTrue(exception.getMessage().contains("mvn test"),
                "Exception message should contain Maven command");
    }

    @Test
    void testMavenExecutionWithMultipleArguments() throws Exception {
        // Given a fake process executor
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);
        Step runMaven = new RunMaven(tempDir, List.of("clean", "package", "-DskipTests"), fakeExecutor);

        // When executing the step
        runMaven.execute();

        // Then the command should include all arguments
        FakeProcessExecutor.ExecutedCommand executed = fakeExecutor.getLastExecutedCommand();
        assertEquals(List.of("mvn", "clean", "package", "-DskipTests"), executed.command(),
                "Should have executed mvn with all arguments");
    }

    @Test
    void testVarargsConstructorCreatesCorrectCommand() throws Exception {
        // Given a RunMaven step created with varargs constructor
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);
        Step runMaven = new RunMaven(tempDir, "clean", "install");

        // When getting the step name
        String name = runMaven.name();

        // Then it should include all Maven arguments
        assertEquals("Run Maven: clean install", name,
                "Step name should include all Maven arguments");
    }

    @Test
    void testNameWithSingleArgument() {
        // Given a RunMaven step with a single argument
        Step runMaven = new RunMaven(tempDir, "validate");

        // When getting the step name
        String name = runMaven.name();

        // Then it should show the argument in the name
        assertEquals("Run Maven: validate", name,
                "Step name should show the Maven goal");
    }

    @Test
    void testNameWithMultipleArguments() {
        // Given a RunMaven step with multiple arguments
        Step runMaven = new RunMaven(tempDir, "clean", "package", "-DskipTests");

        // When getting the step name
        String name = runMaven.name();

        // Then it should show all arguments in the name
        assertEquals("Run Maven: clean package -DskipTests", name,
                "Step name should show all Maven arguments");
    }

    @Test
    void testDifferentExitCodes() {
        // Given a fake process executor that returns various exit codes
        for (int exitCode = 1; exitCode <= 3; exitCode++) {
            FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(exitCode);
            Step runMaven = new RunMaven(tempDir, List.of("test"), fakeExecutor);

            // When executing the step
            // Then it should throw an IOException with the correct exit code
            int expectedExitCode = exitCode;
            IOException exception = assertThrows(IOException.class, runMaven::execute,
                    "Exit code " + expectedExitCode + " should cause exception");

            assertTrue(exception.getMessage().contains("exit code " + expectedExitCode),
                    "Exception should mention exit code " + expectedExitCode);
        }
    }

    @Test
    void testWorkingDirectoryIsPassedCorrectly() throws Exception {
        // Given a fake process executor and a specific working directory
        FakeProcessExecutor fakeExecutor = new FakeProcessExecutor(0);
        Path specificDir = tempDir.resolve("subdir");
        Step runMaven = new RunMaven(specificDir, List.of("compile"), fakeExecutor);

        // When executing the step
        runMaven.execute();

        // Then the working directory should be correct
        FakeProcessExecutor.ExecutedCommand executed = fakeExecutor.getLastExecutedCommand();
        assertEquals(specificDir, executed.workingDirectory(),
                "Should execute in the specified working directory");
    }
}
