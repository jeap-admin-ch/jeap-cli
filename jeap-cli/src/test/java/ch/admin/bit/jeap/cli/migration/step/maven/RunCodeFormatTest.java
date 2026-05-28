package ch.admin.bit.jeap.cli.migration.step.maven;

import ch.admin.bit.jeap.cli.process.FakeProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunCodeFormatTest {

    @TempDir
    Path tempDir;

    @Test
    void skipsWhenNoPluginFound() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                    <artifactId>my-app</artifactId>
                </project>
                """);
        FakeProcessExecutor executor = new FakeProcessExecutor(0);

        new RunCodeFormat(tempDir, executor).execute();

        assertEquals(0, executor.getExecutionCount(), "Should not run Maven when no format plugin is present");
    }

    @Test
    void runsSpotlessApplyWhenSpotlessPluginFound() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.diffplug.spotless</groupId>
                                <artifactId>spotless-maven-plugin</artifactId>
                                <version>3.3.0</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
        FakeProcessExecutor executor = new FakeProcessExecutor(0);

        new RunCodeFormat(tempDir, executor).execute();

        assertEquals(1, executor.getExecutionCount());
        assertTrue(executor.getLastExecutedCommand().command().contains("spotless:apply"));
    }

    @Test
    void runsFormatCodeWhenGitCodeFormatPluginFound() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>com.cosium.code</groupId>
                                <artifactId>git-code-format-maven-plugin</artifactId>
                                <version>6.0</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
        FakeProcessExecutor executor = new FakeProcessExecutor(0);

        new RunCodeFormat(tempDir, executor).execute();

        assertEquals(1, executor.getExecutionCount());
        assertTrue(executor.getLastExecutedCommand().command().contains("com.cosium.code:git-code-format-maven-plugin:format-code"));
    }

    @Test
    void runsBothFormattersWhenBothPluginsFound() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>spotless-maven-plugin</artifactId>
                            </plugin>
                            <plugin>
                                <artifactId>git-code-format-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
        FakeProcessExecutor executor = new FakeProcessExecutor(0);

        new RunCodeFormat(tempDir, executor).execute();

        assertEquals(2, executor.getExecutionCount());
        // spotless runs first, git-code-format second
        List<String> lastCmd = executor.getLastExecutedCommand().command();
        assertTrue(lastCmd.contains("com.cosium.code:git-code-format-maven-plugin:format-code"),
                "Last command should be git-code-format:format-code but was: " + lastCmd);
    }

    @Test
    void detectsPluginInSubModulePom() throws Exception {
        // Root pom has no plugin, sub-module does
        Files.writeString(tempDir.resolve("pom.xml"), "<project><artifactId>root</artifactId></project>");
        Path subModule = Files.createDirectory(tempDir.resolve("module"));
        Files.writeString(subModule.resolve("pom.xml"), """
                <project>
                    <build><plugins><plugin>
                        <artifactId>git-code-format-maven-plugin</artifactId>
                    </plugin></plugins></build>
                </project>
                """);
        FakeProcessExecutor executor = new FakeProcessExecutor(0);

        new RunCodeFormat(tempDir, executor).execute();

        assertEquals(1, executor.getExecutionCount());
        assertEquals(List.of("mvn", "com.cosium.code:git-code-format-maven-plugin:format-code"),
                executor.getLastExecutedCommand().command());
        assertEquals(subModule, executor.getLastExecutedCommand().workingDirectory());
    }

    @Test
    void failsWhenGitCodeFormatFails() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>git-code-format-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
        FakeProcessExecutor executor = new FakeProcessExecutor(1); // simulates failure

        assertThrows(MavenCommandException.class, () -> new RunCodeFormat(tempDir, executor).execute());
    }

    @Test
    void skipsWhenRootDirectoryDoesNotExist() throws Exception {
        Path nonExistent = tempDir.resolve("does-not-exist");
        FakeProcessExecutor executor = new FakeProcessExecutor(0);

        new RunCodeFormat(nonExistent, executor).execute();

        assertEquals(0, executor.getExecutionCount());
    }

    @Test
    void isGitCodeFormatPluginPresentReturnsFalseWhenNoPomExists() throws Exception {
        RunCodeFormat step = new RunCodeFormat(tempDir, new FakeProcessExecutor(0));
        assertFalse(step.isGitCodeFormatPluginPresent());
    }

    @Test
    void isGitCodeFormatPluginPresentReturnsTrueWhenPomContainsPlugin() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<git-code-format-maven-plugin/>");
        RunCodeFormat step = new RunCodeFormat(tempDir, new FakeProcessExecutor(0));
        assertTrue(step.isGitCodeFormatPluginPresent());
    }

    @Test
    void isSpotlessPluginPresentReturnsFalseWhenNoPomExists() throws Exception {
        RunCodeFormat step = new RunCodeFormat(tempDir, new FakeProcessExecutor(0));
        assertFalse(step.isSpotlessPluginPresent());
    }

    @Test
    void isSpotlessPluginPresentReturnsTrueWhenPomContainsPlugin() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<spotless-maven-plugin/>");
        RunCodeFormat step = new RunCodeFormat(tempDir, new FakeProcessExecutor(0));
        assertTrue(step.isSpotlessPluginPresent());
    }
}
