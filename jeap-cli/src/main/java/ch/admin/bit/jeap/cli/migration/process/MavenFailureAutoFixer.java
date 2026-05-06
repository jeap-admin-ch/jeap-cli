package ch.admin.bit.jeap.cli.migration.process;

import ch.admin.bit.jeap.cli.migration.step.maven.MavenCommandException;

import java.nio.file.Path;

/**
 * Attempts to fix Maven failures and returns whether a retry should be attempted.
 */
public interface MavenFailureAutoFixer {

    boolean tryFix(Path root, MavenCommandException failure, int attempt, int maxRetries) throws Exception;
}
