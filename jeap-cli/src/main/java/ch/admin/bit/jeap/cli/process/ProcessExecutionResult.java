package ch.admin.bit.jeap.cli.process;

/**
 * Result of an executed external process.
 *
 * @param exitCode       process exit code
 * @param combinedOutput merged stdout and stderr output
 */
public record ProcessExecutionResult(int exitCode, String combinedOutput) {
}
